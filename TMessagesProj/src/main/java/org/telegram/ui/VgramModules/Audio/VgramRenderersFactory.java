package org.telegram.ui.VgramModules.Audio;

import android.content.Context;

import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;

/** ExoPlayer renderer factory that routes stereo PCM through VGRAM's 8D processor. */
public final class VgramRenderersFactory extends DefaultRenderersFactory {
    private final VgramStereoPanAudioProcessor stereoPanProcessor = new VgramStereoPanAudioProcessor();

    public VgramRenderersFactory(Context context) {
        super(context);
    }

    public VgramStereoPanAudioProcessor getStereoPanProcessor() {
        return stereoPanProcessor;
    }

    @Override
    protected AudioSink buildAudioSink(Context context, boolean enableFloatOutput,
                                        boolean enableAudioTrackPlaybackParams, boolean enableOffload) {
        AudioProcessor[] processors = new AudioProcessor[] { stereoPanProcessor };
        DefaultAudioSink.Builder builder = new DefaultAudioSink.Builder(context)
                .setAudioProcessors(processors)
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams);
        return builder.build();
    }
}
