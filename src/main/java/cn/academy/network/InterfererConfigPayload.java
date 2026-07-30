package cn.academy.network;

import cn.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record InterfererConfigPayload(BlockPos pos, boolean enabled, double range,
                                      List<String> whitelist) implements CustomPacketPayload {
    public static final Type<InterfererConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "interferer_config"));
    public static final StreamCodec<ByteBuf, InterfererConfigPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public InterfererConfigPayload decode(ByteBuf buffer) {
            BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
            boolean enabled = ByteBufCodecs.BOOL.decode(buffer);
            double range = buffer.readDouble();
            int count = Math.min(64, ByteBufCodecs.VAR_INT.decode(buffer));
            List<String> whitelist = new ArrayList<>(count);
            for (int i = 0; i < count; i++) whitelist.add(ByteBufCodecs.STRING_UTF8.decode(buffer));
            return new InterfererConfigPayload(pos, enabled, range, List.copyOf(whitelist));
        }
        @Override public void encode(ByteBuf buffer, InterfererConfigPayload value) {
            BlockPos.STREAM_CODEC.encode(buffer, value.pos);
            ByteBufCodecs.BOOL.encode(buffer, value.enabled);
            buffer.writeDouble(value.range);
            ByteBufCodecs.VAR_INT.encode(buffer, Math.min(64, value.whitelist.size()));
            for (int i = 0; i < Math.min(64, value.whitelist.size()); i++)
                ByteBufCodecs.STRING_UTF8.encode(buffer, value.whitelist.get(i));
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
