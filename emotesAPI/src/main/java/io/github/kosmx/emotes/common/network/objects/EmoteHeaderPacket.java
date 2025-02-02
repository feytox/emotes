package io.github.kosmx.emotes.common.network.objects;

import io.github.kosmx.emotes.common.emote.EmoteData;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class EmoteHeaderPacket extends AbstractNetworkPacket{
    @Override
    public byte getID() {
        return 0x11;
    }

    @Override
    public byte getVer() {
        return 1;
    }

    @Override
    public boolean read(ByteBuffer byteBuffer, NetData config, int version) throws IOException {
        EmoteData.EmoteBuilder builder = config.getEmoteBuilder();
        builder.name = readString(byteBuffer);
        builder.description = readString(byteBuffer);
        builder.author = readString(byteBuffer);
        return true;
    }

    @Override
    public void write(ByteBuffer byteBuffer, NetData config) throws IOException {
        writeString(byteBuffer, config.emoteData.name);
        writeString(byteBuffer, config.emoteData.description);
        writeString(byteBuffer, config.emoteData.author);
    }

    @Override
    public boolean doWrite(NetData config) {
        return config.emoteData != null && config.purpose.exchangeHeader;
    }

    @Override
    public int calculateSize(NetData config) {
        EmoteData emote = config.emoteData;
        return sumStrings(emote.name, emote.author, emote.description);
    }

    public static void writeString(ByteBuffer byteBuffer, String s){
        if(s == null){
            byteBuffer.putInt(0);
            return;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        byteBuffer.putInt(bytes.length);
        byteBuffer.put(bytes);
    }
    public static String readString(ByteBuffer byteBuffer){
        int len = byteBuffer.getInt();
        if(len == 0)return null;
        byte[] bytes = new byte[len];
        byteBuffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static int sumStrings(String... strings){
        int size = 0;
        for(String s : strings){
            if(s == null) size += 4;
            else size += s.getBytes(StandardCharsets.UTF_8).length + 4;
        }
        return size;
    }
}
