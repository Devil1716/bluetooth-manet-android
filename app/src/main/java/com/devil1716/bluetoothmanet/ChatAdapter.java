package com.devil1716.bluetoothmanet;

import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int HEADER = 0;
    private static final int MESSAGE = 1;
    private final List<Object> rows = new ArrayList<>();

    public void setMessages(List<ChatMessageEntity> messages) {
        rows.clear();
        String previousConversation = null;
        for (ChatMessageEntity message : messages) {
            if (!message.conversationId.equals(previousConversation)) {
                rows.add(message.conversationId);
                previousConversation = message.conversationId;
            }
            rows.add(message);
        }
        notifyDataSetChanged();
    }

    @Override public int getItemViewType(int position) {
        return rows.get(position) instanceof String ? HEADER : MESSAGE;
    }

    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        if (type == HEADER) {
            TextView view = new TextView(parent.getContext());
            view.setPadding(8, 20, 8, 8);
            view.setTextColor(Color.DKGRAY);
            view.setTextSize(14);
            return new HeaderHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new MessageHolder(view);
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).view.setText("Conversation · " + rows.get(position));
            return;
        }
        ChatMessageEntity message = (ChatMessageEntity) rows.get(position);
        MessageHolder messageHolder = (MessageHolder) holder;
        messageHolder.text.setText(message.text);
        String metadata = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(message.timestamp));
        if (message.sentByMe) metadata += "  ·  " + message.status.name();
        messageHolder.meta.setText(metadata);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) messageHolder.bubble.getLayoutParams();
        params.gravity = message.sentByMe ? Gravity.END : Gravity.START;
        messageHolder.bubble.setLayoutParams(params);
        messageHolder.bubble.setBackgroundColor(message.sentByMe ? Color.rgb(210, 232, 255) : Color.rgb(232, 240, 232));
    }

    @Override public int getItemCount() { return rows.size(); }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView view;
        HeaderHolder(View view) { super(view); this.view = (TextView) view; }
    }

    static class MessageHolder extends RecyclerView.ViewHolder {
        final LinearLayout bubble;
        final TextView text;
        final TextView meta;
        MessageHolder(View itemView) {
            super(itemView);
            bubble = itemView.findViewById(R.id.messageBubble);
            text = itemView.findViewById(R.id.messageText);
            meta = itemView.findViewById(R.id.messageMeta);
        }
    }
}
