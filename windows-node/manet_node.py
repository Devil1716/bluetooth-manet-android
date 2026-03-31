import argparse
import asyncio
import sys
import uuid
from dataclasses import dataclass
from typing import Optional

from winrt.windows.devices.bluetooth import BluetoothDevice
from winrt.windows.devices.bluetooth.rfcomm import (
    RfcommDeviceService,
    RfcommServiceId,
    RfcommServiceProvider,
)
from winrt.windows.devices.enumeration import DeviceInformation
from winrt.windows.networking.sockets import StreamSocket, StreamSocketListener
from winrt.windows.storage.streams import DataReader, DataWriter, InputStreamOptions, UnicodeEncoding

SERVICE_UUID = uuid.UUID("12345678-1234-1234-1234-123456789abc")
SERVICE_ID = RfcommServiceId.from_uuid(SERVICE_UUID)
DEFAULT_TTL = 3


@dataclass(frozen=True)
class ManetMessage:
    message_id: str
    source: str
    destination: str
    ttl: int
    data: str

    @classmethod
    def outbound(cls, source: str, destination: str, data: str, ttl: int = DEFAULT_TTL) -> "ManetMessage":
        return cls(str(uuid.uuid4()), source.upper(), destination.upper(), ttl, data)

    @classmethod
    def from_wire(cls, payload: str) -> "ManetMessage":
        parts = payload.split("|", 4)
        if len(parts) != 5:
            raise ValueError("Expected ID|SRC|DEST|TTL|DATA")
        return cls(parts[0], parts[1], parts[2], int(parts[3]), parts[4])

    def to_wire(self) -> str:
        return f"{self.message_id}|{self.source}|{self.destination}|{self.ttl}|{self.data}"

    def decrement_ttl(self) -> "ManetMessage":
        return ManetMessage(self.message_id, self.source, self.destination, self.ttl - 1, self.data)


class PeerConnection:
    def __init__(self, label: str, socket: StreamSocket):
        self.label = label
        self.socket = socket
        self.reader = DataReader(socket.input_stream)
        self.reader.unicode_encoding = UnicodeEncoding.UTF8
        self.reader.input_stream_options = InputStreamOptions.PARTIAL
        self.writer = DataWriter(socket.output_stream)
        self.writer.unicode_encoding = UnicodeEncoding.UTF8

    async def read_line(self) -> str | None:
        chars: list[str] = []
        while True:
            loaded = await self.reader.load_async(1)
            if loaded == 0:
                return None
            char = self.reader.read_string(1)
            if char == "\n":
                return "".join(chars).rstrip("\r")
            chars.append(char)

    async def write_line(self, payload: str) -> None:
        self.writer.write_string(payload + "\n")
        await self.writer.store_async()
        await self.writer.flush_async()

    def close(self) -> None:
        try:
            self.reader.close()
        except Exception:
            pass
        try:
            self.writer.close()
        except Exception:
            pass
        try:
            self.socket.close()
        except Exception:
            pass


class WindowsManetNode:
    def __init__(self, node_id: str):
        self.node_id = node_id.upper()
        self.connections: dict[str, PeerConnection] = {}
        self.seen_messages: set[str] = set()
        self.listener: StreamSocketListener | None = None
        self.provider: RfcommServiceProvider | None = None
        self.reader_tasks: set[asyncio.Task] = set()
        self.loop: Optional[asyncio.AbstractEventLoop] = None

    async def start_listener(self) -> None:
        self.loop = asyncio.get_running_loop()
        self.provider = await RfcommServiceProvider.create_async(SERVICE_ID)
        self.listener = StreamSocketListener()
        self.listener.add_connection_received(self._on_connection_received)
        await self.listener.bind_service_name_async(self.provider.service_id.as_string())
        self.provider.start_advertising_with_radio_discoverability(self.listener, True)
        print(f"[listen] advertising {self.provider.service_id.as_string()} for node {self.node_id}")

    async def stop(self) -> None:
        for task in list(self.reader_tasks):
            task.cancel()
        for peer in list(self.connections.values()):
            peer.close()
        self.connections.clear()
        if self.provider is not None:
            try:
                self.provider.stop_advertising()
            except Exception:
                pass
        if self.listener is not None:
            try:
                self.listener.close()
            except Exception:
                pass

    async def list_bluetooth_devices(self) -> list[tuple[str, str]]:
        devices = await DeviceInformation.find_all_async()
        results: list[tuple[str, str]] = []
        for device in devices:
            if "BTHENUM#Dev_" not in device.id:
                continue
            label = device.name or "Unknown"
            item = (label, device.id)
            if item not in results:
                results.append(item)
        return results

    async def connect_to_device(self, device_id: str) -> None:
        bt_device = await BluetoothDevice.from_id_async(device_id)
        if bt_device is None:
            raise RuntimeError("Bluetooth device not found. Pair it in Windows first.")

        service_result = await bt_device.get_rfcomm_services_for_id_async(SERVICE_ID)
        services = list(service_result.services)
        if not services:
            raise RuntimeError(
                "The target does not advertise the MANET RFCOMM service. "
                "Make sure the Android app is listening and the phone is discoverable."
            )

        service = services[0]
        socket = StreamSocket()
        await socket.connect_async(service.connection_host_name, service.connection_service_name)
        label = f"{bt_device.name} [{device_id}]"
        await self._register_connection(label, socket)
        print(f"[connect] connected to {label}")

    async def send_message(self, destination: str, body: str) -> None:
        destination = destination.strip().upper()
        body = body.strip()
        if not destination or not body:
            print("[send] destination and message are required")
            return

        message = ManetMessage.outbound(self.node_id, destination, body)
        self.seen_messages.add(message.message_id)
        if destination == self.node_id:
            print(f"[deliver] {message.source} -> {message.destination}: {message.data}")
            return
        await self.forward_message(message, None)

    async def forward_message(self, message: ManetMessage, except_label: str | None) -> None:
        forwarded = 0
        for label, peer in list(self.connections.items()):
            if label == except_label:
                continue
            try:
                await peer.write_line(message.to_wire())
                forwarded += 1
            except Exception as exc:
                print(f"[forward] failed via {label}: {exc}")
                self._drop_connection(label)
        print(f"[forward] {message.message_id} -> {forwarded} peer(s)")

    async def _register_connection(self, label: str, socket: StreamSocket) -> None:
        if label in self.connections:
            self._drop_connection(label)
        peer = PeerConnection(label, socket)
        self.connections[label] = peer
        task = asyncio.create_task(self._reader_loop(peer))
        self.reader_tasks.add(task)
        task.add_done_callback(self.reader_tasks.discard)

    async def _reader_loop(self, peer: PeerConnection) -> None:
        print(f"[peer] active {peer.label}")
        try:
            while True:
                payload = await peer.read_line()
                if payload is None:
                    break
                await self.handle_incoming(payload, peer.label)
        except asyncio.CancelledError:
            pass
        except Exception as exc:
            print(f"[peer] read failed from {peer.label}: {exc}")
        finally:
            self._drop_connection(peer.label)

    async def handle_incoming(self, payload: str, source_label: str) -> None:
        try:
            message = ManetMessage.from_wire(payload)
        except Exception:
            print(f"[rx] ignored malformed payload from {source_label}: {payload}")
            return

        if message.message_id in self.seen_messages:
            return

        self.seen_messages.add(message.message_id)
        print(f"[rx] {message.source} -> {message.destination} ttl={message.ttl} via {source_label}")

        if message.destination.upper() == self.node_id:
            print(f"[deliver] {message.source} -> {message.destination}: {message.data}")
            return

        if message.ttl <= 1:
            print(f"[drop] {message.message_id} TTL expired")
            return

        await self.forward_message(message.decrement_ttl(), source_label)

    def _drop_connection(self, label: str) -> None:
        peer = self.connections.pop(label, None)
        if peer is not None:
            peer.close()
            print(f"[peer] closed {label}")

    def _on_connection_received(self, _sender, args) -> None:
        label = f"incoming-{len(self.connections) + 1}"
        if self.loop is None:
            print("[peer] no event loop available for incoming connection")
            try:
                args.socket.close()
            except Exception:
                pass
            return
        self.loop.call_soon_threadsafe(self._schedule_incoming_connection, label, args.socket)

    def _schedule_incoming_connection(self, label: str, socket: StreamSocket) -> None:
        task = self.loop.create_task(self._register_connection(label, socket))
        self.reader_tasks.add(task)
        task.add_done_callback(self.reader_tasks.discard)


async def command_loop(node: WindowsManetNode) -> None:
    print("Commands: devices, connect <index>, peers, send <DEST> <MESSAGE>, quit")
    while True:
        raw = await asyncio.to_thread(input, "manet> ")
        command = raw.strip()
        if not command:
            continue

        if command == "quit":
            return

        if command == "devices":
            devices = await node.list_bluetooth_devices()
            if not devices:
                print("No paired Bluetooth devices found. Pair the phone in Windows first.")
                continue
            for index, (name, device_id) in enumerate(devices, start=1):
                print(f"{index}. {name}\n   {device_id}")
            continue

        if command.startswith("connect "):
            devices = await node.list_bluetooth_devices()
            try:
                index = int(command.split(maxsplit=1)[1]) - 1
                _, device_id = devices[index]
            except Exception:
                print("Usage: connect <device number from 'devices'>")
                continue
            try:
                await node.connect_to_device(device_id)
            except Exception as exc:
                print(f"[connect] failed: {exc}")
            continue

        if command == "peers":
            if not node.connections:
                print("No active peers.")
                continue
            for label in node.connections:
                print(label)
            continue

        if command.startswith("send "):
            parts = command.split(maxsplit=2)
            if len(parts) < 3:
                print("Usage: send <DEST> <MESSAGE>")
                continue
            await node.send_message(parts[1], parts[2])
            continue

        print("Unknown command.")


async def async_main() -> int:
    parser = argparse.ArgumentParser(description="Windows RFCOMM MANET node for the Android Bluetooth demo.")
    parser.add_argument("--node-id", default="LAPTOP", help="Node ID for this laptop, for example B or LAPTOP")
    args = parser.parse_args()

    node = WindowsManetNode(args.node_id)
    await node.start_listener()
    try:
        await command_loop(node)
    finally:
        await node.stop()
    return 0


def main() -> int:
    try:
        return asyncio.run(async_main())
    except KeyboardInterrupt:
        print("\nExiting...")
        return 0


if __name__ == "__main__":
    sys.exit(main())
