package com.github.tatercertified.slopium.mixin;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.CompressionEncoder;
import net.minecraft.network.VarInt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.nio.ByteBuffer;
import java.util.zip.Deflater;

@Mixin(CompressionEncoder.class)
public class CompressionEncoderMixin {
    @Shadow private byte[] encodeBuf;
    @Shadow private Deflater deflater;
    @Shadow private int threshold;

    @Unique
    private byte[] slopium$heapScratch = new byte[8192];

    @Unique
    private byte[] slopium$ensureScratch(int size) {
        byte[] scratch = this.slopium$heapScratch;
        if (scratch.length < size) {
            scratch = new byte[Integer.highestOneBit(size - 1) << 1];
            this.slopium$heapScratch = scratch;
        }

        return scratch;
    }

    /**
     * @author tatercertified
     * @reason Avoids per-packet heap allocation and uses zero-copy inputs where Netty exposes them.
     */
    @Overwrite
    protected void encode(ChannelHandlerContext context, ByteBuf input, ByteBuf output) {
        final int readableBytes = input.readableBytes();
        if (readableBytes > 8_388_608) {
            throw new IllegalArgumentException("Packet too big (is " + readableBytes + ", should be less than 8388608)");
        }

        if (readableBytes < this.threshold) {
            VarInt.write(output, 0);
            output.writeBytes(input, input.readerIndex(), readableBytes);
            input.skipBytes(readableBytes);
            return;
        }

        VarInt.write(output, readableBytes);

        try {
            if (input.hasArray()) {
                final int offset = input.arrayOffset() + input.readerIndex();
                this.deflater.setInput(input.array(), offset, readableBytes);
                input.skipBytes(readableBytes);
            } else if (input.nioBufferCount() == 1) {
                final ByteBuffer nioBuffer = input.nioBuffer(input.readerIndex(), readableBytes);
                this.deflater.setInput(nioBuffer);
                input.skipBytes(readableBytes);
            } else {
                final byte[] scratch = this.slopium$ensureScratch(readableBytes);
                input.readBytes(scratch, 0, readableBytes);
                this.deflater.setInput(scratch, 0, readableBytes);
            }

            this.deflater.finish();
            while (!this.deflater.finished()) {
                final int written = this.deflater.deflate(this.encodeBuf);
                output.writeBytes(this.encodeBuf, 0, written);
            }
        } finally {
            this.deflater.reset();
        }
    }
}
