package com.github.tatercertified.slopium.mixin;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.CompressionDecoder;
import net.minecraft.network.VarInt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

@Mixin(CompressionDecoder.class)
public class CompressionDecoderMixin {
    @Shadow private Inflater inflater;
    @Shadow private int threshold;
    @Shadow private boolean validateDecompressed;

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
     * @reason Avoids unnecessary buffer copies and direct-buffer allocation during packet decompression.
     */
    @Overwrite
    protected void decode(ChannelHandlerContext context, ByteBuf input, List<Object> output) throws Exception {
        final int claimedSize = VarInt.read(input);
        if (claimedSize == 0) {
            output.add(input.readRetainedSlice(input.readableBytes()));
            return;
        }

        if (this.validateDecompressed) {
            if (claimedSize < this.threshold) {
                throw new DecoderException("Badly compressed packet - size of " + claimedSize + " is below server threshold of " + this.threshold);
            }

            if (claimedSize > 8_388_608) {
                throw new DecoderException("Badly compressed packet - size of " + claimedSize + " is larger than protocol maximum of 8388608");
            }
        }

        if (input.hasArray()) {
            final int offset = input.arrayOffset() + input.readerIndex();
            this.inflater.setInput(input.array(), offset, input.readableBytes());
            input.skipBytes(input.readableBytes());
        } else if (input.nioBufferCount() == 1) {
            final int readableBytes = input.readableBytes();
            final ByteBuffer nioBuffer = input.nioBuffer(input.readerIndex(), readableBytes);
            this.inflater.setInput(nioBuffer);
            input.skipBytes(readableBytes);
        } else {
            final int readableBytes = input.readableBytes();
            final byte[] scratch = this.slopium$ensureScratch(readableBytes);
            input.readBytes(scratch, 0, readableBytes);
            this.inflater.setInput(scratch, 0, readableBytes);
        }

        final ByteBuf inflated = context.alloc().directBuffer(claimedSize);
        try {
            final ByteBuffer outputBuffer = inflated.internalNioBuffer(0, claimedSize);
            final int startPosition = outputBuffer.position();
            this.inflater.inflate(outputBuffer);
            final int written = outputBuffer.position() - startPosition;
            if (written != claimedSize) {
                throw new DecoderException("Badly compressed packet - actual length of uncompressed payload " + written + " does not match declared size " + claimedSize);
            }

            inflated.writerIndex(written);
            output.add(inflated);
        } catch (Exception exception) {
            inflated.release();
            throw exception;
        } finally {
            this.inflater.reset();
        }
    }
}
