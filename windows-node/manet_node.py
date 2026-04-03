import argparse
import asyncio
import ctypes
import os
import sys
import time
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


class Style:
    RESET = "\033[0m"
    BOLD = "\033[1m"
    DIM = "\033[2m"
    CYAN = "\033[96m"
    BLUE = "\033[94m"
    GREEN = "\033[92m"
    YELLOW = "\033[93m"
    MAGENTA = "\033[95m"
    RED = "\033[91m"


ASCII_FIGURE = [
    "        /########\\        ",
    "       /##########\\       ",
    "      /###  ##  ###\\      ",
    "     /#### MESH ####\\     ",
    "    |##### SHIELD #####|    ",
    "    |###### || ######|    ",
    "    |###### || ######|    ",
    "     \\##### || #####/     ",
    "      \\#### || ####/      ",
    "       \\##########/       ",
    "        \\########/        ",
]


def enable_ansi_colors() -> None:
    if os.name != "nt":
        return
    try:
        handle = ctypes.windll.kernel32.GetStdHandle(-11)
        mode = ctypes.c_uint32()
        if ctypes.windll.kernel32.GetConsoleMode(handle, ctypes.byref(mode)):
            ctypes.windll.kernel32.SetConsoleMode(handle, mode.value | 0x0004)
    except Exception:
        pass


def colorize(text: str, *styles: str) -> str:
    return "".join(styles) + text + Style.RESET


def animate_startup() -> None:
    palette = [
        (Style.CYAN, Style.BOLD),
        (Style.BLUE, Style.BOLD),
        (Style.MAGENTA, Style.BOLD),
    ]
    for styles in palette:
        clear_screen()
        print()
        for line in ASCII_FIGURE:
            print(colorize(line.center(72), *styles))
        print()
        print(colorize("Booting Bluetooth mesh terminal...", Style.DIM, Style.CYAN).center(72))
        time.sleep(0.14)
    clear_screen()


def make_box(title: str, lines: list[str], width: int = 46) -> list[str]:
    top = colorize("+" + "-" * (width - 2) + "+", Style.BLUE)
    bottom = colorize("+" + "-" * (width - 2) + "+", Style.BLUE)
    title_text = f" {title} "
    header = colorize("|", Style.BLUE) + colorize(title_text.ljust(width - 2), Style.BOLD, Style.CYAN) + colorize("|", Style.BLUE)
    body = []
    for line in lines:
        body.append(colorize("|", Style.BLUE) + line.ljust(width - 2) + colorize("|", Style.BLUE))
    return [top, header] + body + [bottom]


def render_dashboard(node: "WindowsManetNode") -> None:
    clear_screen()
    left_box = make_box(
        "MANET",
        [colorize(line.center(42), Style.MAGENTA, Style.BOLD) for line in ASCII_FIGURE]
        + [colorize("bluetooth mesh terminal".center(42), Style.DIM)],
    )
    recent = node.event_log[-8:] if node.event_log else ["No activity yet."]
    right_box = make_box(
        "Session",
        [
            colorize(f"node     {node.node_id}", Style.GREEN, Style.BOLD),
            colorize(f"service  {SERVICE_UUID}", Style.YELLOW),
            colorize(f"peers    {len(node.connections)}", Style.GREEN),
            colorize(f"seen     {len(node.seen_messages)}", Style.GREEN),
            colorize(f"devices  {len(node.cached_devices)}", Style.GREEN),
            "",
            colorize("recent", Style.CYAN, Style.BOLD),
        ] + [colorize(f"- {line[:34]}", Style.DIM) for line in recent[:6]],
    )
    max_lines = max(len(left_box), len(right_box))
    left_box.extend([" " * 46] * (max_lines - len(left_box)))
    right_box.extend([" " * 46] * (max_lines - len(right_box)))
    for left, right in zip(left_box, right_box):
        print(left + "  " + right)
    print()
    print(colorize("Commands", Style.BOLD, Style.CYAN) + colorize("  menu  status  devices  connect <n>  send <DEST> <MSG>  inbox  logs  quit", Style.DIM))
    print(colorize("-" * 94, Style.BLUE))


def print_help() -> None:
    print(
        colorize("\nCommands:\n", Style.BOLD, Style.CYAN)
        + "  menu                 guided action picker\n"
        + "  status               show node status\n"
        + "  devices              refresh paired bluetooth devices\n"
        + "  connect <index>      connect to device from latest device list\n"
        + "  peers                show active rfcomm peers\n"
        + "  send <DEST> <MSG>    send a message into the mesh\n"
        + "  inbox                show delivered messages\n"
        + "  logs                 show recent event log lines\n"
        + "  clear                redraw the dashboard\n"
        + "  help                 show this help text\n"
        + "  quit                 stop the node\n"
    )


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
        self.cached_devices: list[tuple[str, str]] = []
        self.delivered_messages: list[str] = []
        self.event_log: list[str] = []

    def log(self, message: str) -> None:
        entry = message.strip()
        self.event_log.append(entry)
        self.event_log = self.event_log[-50:]
        styled = entry
        if entry.startswith("[listen]") or entry.startswith("[connect]") or entry.startswith("[deliver]"):
            styled = colorize(entry, Style.GREEN)
        elif entry.startswith("[forward]") or entry.startswith("[rx]"):
            styled = colorize(entry, Style.CYAN)
        elif entry.startswith("[drop]"):
            styled = colorize(entry, Style.YELLOW)
        elif entry.startswith("[peer]") and "failed" in entry.lower():
            styled = colorize(entry, Style.RED)
        elif entry.startswith("[peer]"):
            styled = colorize(entry, Style.MAGENTA)
        elif "failed" in entry.lower() or "no event loop" in entry.lower():
            styled = colorize(entry, Style.RED)
        print(styled)

    async def start_listener(self) -> None:
        self.loop = asyncio.get_running_loop()
        self.provider = await RfcommServiceProvider.create_async(SERVICE_ID)
        self.listener = StreamSocketListener()
        self.listener.add_connection_received(self._on_connection_received)
        await self.listener.bind_service_name_async(self.provider.service_id.as_string())
        self.provider.start_advertising_with_radio_discoverability(self.listener, True)
        self.log(f"[listen] advertising {self.provider.service_id.as_string()} for node {self.node_id}")

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
        self.cached_devices = results
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
        self.log(f"[connect] connected to {label}")

    async def send_message(self, destination: str, body: str) -> None:
        destination = destination.strip().upper()
        body = body.strip()
        if not destination or not body:
            self.log("[send] destination and message are required")
            return

        message = ManetMessage.outbound(self.node_id, destination, body)
        self.seen_messages.add(message.message_id)
        if destination == self.node_id:
            self.log(f"[deliver] {message.source} -> {message.destination}: {message.data}")
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
                self.log(f"[forward] failed via {label}: {exc}")
                self._drop_connection(label)
        self.log(f"[forward] {message.message_id} -> {forwarded} peer(s)")

    async def _register_connection(self, label: str, socket: StreamSocket) -> None:
        if label in self.connections:
            self._drop_connection(label)
        peer = PeerConnection(label, socket)
        self.connections[label] = peer
        task = asyncio.create_task(self._reader_loop(peer))
        self.reader_tasks.add(task)
        task.add_done_callback(self.reader_tasks.discard)

    async def _reader_loop(self, peer: PeerConnection) -> None:
        self.log(f"[peer] active {peer.label}")
        try:
            while True:
                payload = await peer.read_line()
                if payload is None:
                    break
                await self.handle_incoming(payload, peer.label)
        except asyncio.CancelledError:
            pass
        except Exception as exc:
            self.log(f"[peer] read failed from {peer.label}: {exc}")
        finally:
            self._drop_connection(peer.label)

    async def handle_incoming(self, payload: str, source_label: str) -> None:
        try:
            message = ManetMessage.from_wire(payload)
        except Exception:
            self.log(f"[rx] ignored malformed payload from {source_label}: {payload}")
            return

        if message.message_id in self.seen_messages:
            return

        self.seen_messages.add(message.message_id)
        self.log(f"[rx] {message.source} -> {message.destination} ttl={message.ttl} via {source_label}")

        if message.destination.upper() == self.node_id:
            delivered = f"{message.source} -> {message.destination}: {message.data}"
            self.delivered_messages.append(delivered)
            self.delivered_messages = self.delivered_messages[-20:]
            self.log(f"[deliver] {delivered}")
            return

        if message.ttl <= 1:
            self.log(f"[drop] {message.message_id} TTL expired")
            return

        await self.forward_message(message.decrement_ttl(), source_label)

    def _drop_connection(self, label: str) -> None:
        peer = self.connections.pop(label, None)
        if peer is not None:
            peer.close()
            self.log(f"[peer] closed {label}")

    def _on_connection_received(self, _sender, args) -> None:
        label = f"incoming-{len(self.connections) + 1}"
        if self.loop is None:
            self.log("[peer] no event loop available for incoming connection")
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

    def print_status(self) -> None:
        print(
            f"\nStatus\n"
            f"  Node ID     : {self.node_id}\n"
            f"  Peers       : {len(self.connections)}\n"
            f"  Seen msgs   : {len(self.seen_messages)}\n"
            f"  Cached devs : {len(self.cached_devices)}\n"
        )

    def print_devices(self) -> None:
        if not self.cached_devices:
            print("No paired Bluetooth devices found. Run 'devices' to refresh.")
            return
        print("\nPaired Devices")
        for index, (name, device_id) in enumerate(self.cached_devices, start=1):
            print(f"  {index}. {name}")
            print(f"     {device_id}")

    def print_peers(self) -> None:
        if not self.connections:
            print("No active peers.")
            return
        print("\nActive Peers")
        for index, label in enumerate(self.connections, start=1):
            print(f"  {index}. {label}")

    def print_inbox(self) -> None:
        if not self.delivered_messages:
            print("No delivered messages yet.")
            return
        print("\nInbox")
        for line in self.delivered_messages[-10:]:
            print(f"  {line}")

    def print_logs(self) -> None:
        if not self.event_log:
            print("No log lines yet.")
            return
        print("\nRecent Logs")
        for line in self.event_log[-15:]:
            print(f"  {line}")


async def command_loop(node: WindowsManetNode) -> None:
    render_dashboard(node)
    print_help()
    while True:
        raw = await asyncio.to_thread(input, colorize("› ", Style.BOLD, Style.CYAN))
        command = raw.strip()
        if not command:
            render_dashboard(node)
            continue

        if command in {"quit", "exit"}:
            return

        if command in {"help", "?"}:
            print_help()
            continue

        if command == "menu":
            await guided_menu(node)
            render_dashboard(node)
            continue

        if command == "status":
            render_dashboard(node)
            node.print_status()
            continue

        if command == "devices":
            await node.list_bluetooth_devices()
            render_dashboard(node)
            node.print_devices()
            continue

        if command.startswith("connect "):
            try:
                index = int(command.split(maxsplit=1)[1]) - 1
                if not node.cached_devices:
                    await node.list_bluetooth_devices()
                _, device_id = node.cached_devices[index]
            except Exception:
                print("Usage: connect <device number from 'devices'>")
                continue
            try:
                await node.connect_to_device(device_id)
            except Exception as exc:
                print(f"[connect] failed: {exc}")
            render_dashboard(node)
            continue

        if command == "peers":
            render_dashboard(node)
            node.print_peers()
            continue

        if command.startswith("send "):
            parts = command.split(maxsplit=2)
            if len(parts) < 3:
                print("Usage: send <DEST> <MESSAGE>")
                continue
            await node.send_message(parts[1], parts[2])
            render_dashboard(node)
            continue

        if command == "inbox":
            render_dashboard(node)
            node.print_inbox()
            continue

        if command == "logs":
            render_dashboard(node)
            node.print_logs()
            continue

        if command == "clear":
            render_dashboard(node)
            continue

        print("Unknown command.")
        render_dashboard(node)


async def guided_menu(node: WindowsManetNode) -> None:
    print(
        "\nMenu\n"
        "  1. Refresh paired devices\n"
        "  2. Connect to a paired device\n"
        "  3. Show active peers\n"
        "  4. Send a message\n"
        "  5. Show inbox\n"
        "  6. Show recent logs\n"
        "  7. Show status\n"
        "  8. Clear screen\n"
        "  9. Return\n"
    )
    choice = (await asyncio.to_thread(input, "Choose an action: ")).strip()
    if choice == "1":
        await node.list_bluetooth_devices()
        node.print_devices()
    elif choice == "2":
        await node.list_bluetooth_devices()
        node.print_devices()
        if not node.cached_devices:
            return
        picked = (await asyncio.to_thread(input, "Device number: ")).strip()
        try:
            index = int(picked) - 1
            _, device_id = node.cached_devices[index]
            await node.connect_to_device(device_id)
        except Exception as exc:
            print(f"[connect] failed: {exc}")
    elif choice == "3":
        node.print_peers()
    elif choice == "4":
        destination = (await asyncio.to_thread(input, "Destination node ID: ")).strip()
        body = (await asyncio.to_thread(input, "Message: ")).strip()
        await node.send_message(destination, body)
    elif choice == "5":
        node.print_inbox()
    elif choice == "6":
        node.print_logs()
    elif choice == "7":
        node.print_status()
    elif choice == "8":
        clear_screen()


def clear_screen() -> None:
    os.system("cls" if os.name == "nt" else "clear")


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
        enable_ansi_colors()
        animate_startup()
        return asyncio.run(async_main())
    except KeyboardInterrupt:
        print(colorize("\nExiting...", Style.DIM, Style.YELLOW))
        return 0


if __name__ == "__main__":
    sys.exit(main())
