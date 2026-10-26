package org.telegram.ui.VgramModules.Audio;

import androidx.media3.common.C;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.audio.AudioProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Applies independent gain to interleaved PCM16 stereo samples without per-frame allocations. */
public final class VgramStereoPanAudioProcessor extends BaseAudioProcessor {
    private volatile float leftGain = 1f;
    private volatile float rightGain = 1f;

    public void setPhase(float phase) {
        float p = Math.max(0f, Math.min(1f, phase)) * (float) (Math.PI * 0.5);
        leftGain = (float) Math.cos(p);
        rightGain = (float) Math.sin(p);
    }

    public void resetPan() {
        leftGain = 1f;
        rightGain = 1f;
    }

    @Override protected AudioFormat onConfigure(AudioFormat inputAudioFormat) throws UnhandledAudioFormatException {
        if (inputAudioFormat.channelCount == 2 && inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            return inputAudioFormat;
        }
        return AudioFormat.NOT_SET;
    }

    @Override protected void onQueueInput(ByteBuffer inputBuffer) {
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN);
        int remaining = inputBuffer.remaining();
        ByteBuffer output = replaceOutputBuffer(remaining).order(ByteOrder.LITTLE_ENDIAN);
        float left = leftGain;
        float right = rightGain;
        while (inputBuffer.remaining() >= 4) {
            int l = inputBuffer.getShort();
            int r = inputBuffer.getShort();
            output.putShort(clamp16(Math.round(l * left)));
            output.putShort(clamp16(Math.round(r * right)));
        }
        while (inputBuffer.hasRemaining()) output.put(inputBuffer.get());
        output.flip();
    }

    private static short clamp16(int value) { return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value)); }
}
