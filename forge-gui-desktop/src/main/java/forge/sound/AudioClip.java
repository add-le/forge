/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2012  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package forge.sound;

import javax.sound.sampled.*;

import forge.properties.ForgeConstants;

import java.io.File;
import java.io.IOException;
import java.util.MissingResourceException;
import java.util.function.Supplier;


/**
 * SoundSystem - a simple sound playback system for Forge.
 * Do not use directly. Instead, use the {@link forge.sound.SoundEffectType} enumeration.
 * 
 * @author Agetian
 */
public class AudioClip implements IAudioClip {
    private Clip clip;
    private boolean started;
    private boolean looping;

    public static boolean fileExists(String fileName) {
        File fSound = new File(ForgeConstants.SOUND_DIR, fileName);
        return fSound.exists();
    }

    public AudioClip(final String filename) {
        File fSound = new File(ForgeConstants.SOUND_DIR, filename);
        if (!fSound.exists()) {
            throw new IllegalArgumentException("Sound file " + fSound.toString() + " does not exist, cannot make a clip of it");
        }

        try {
            AudioInputStream stream = AudioSystem.getAudioInputStream(fSound);
            AudioFormat format = stream.getFormat();
            DataLine.Info info = new DataLine.Info(Clip.class, stream.getFormat(), ((int) stream.getFrameLength() * format.getFrameSize()));
            clip = (Clip) AudioSystem.getLine(info);
            clip.addLineListener(this::lineStatusChanged);
            clip.open(stream);
            return;

        } catch (IOException ex) {
            System.err.println("Unable to load sound file: " + filename);
        } catch (LineUnavailableException ex) {
            System.err.println("Error initializing sound system: " + ex);
        } catch (UnsupportedAudioFileException ex) {
            System.err.println("Unsupported file type of the sound file: " + fSound.toString() + " - " + ex.getMessage());
            clip = null;
            return;
        }
        throw new MissingResourceException("Sound clip failed to load", this.getClass().getName(), filename);
    }

    @Override
    public final void play() {
        if (null == clip) {
            return;
        }
        synchronized (this) {
            if (clip.isRunning()) {
                // introduce small delay to make a batch sounds more granular,
                // e.g. when you auto-tap 4 lands the 4 tap sounds should
                // not become completely merged
                waitSoundSystemDelay();
            }
            clip.setMicrosecondPosition(0);
            if (!this.looping && clip.isRunning()) {
                return;
            }
            this.started = false;
            clip.start();
            wait(() -> this.started);
        }
    }

    @Override
    public final void loop() {
        if (null == clip) {
            return;
        }
        synchronized (this) {
            clip.setMicrosecondPosition(0);
            if (this.looping && clip.isRunning()) {
                return;
            }
            this.started = false;
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            wait(() -> this.started);
            this.looping = true;
        }
    }

    @Override
    public final void stop() {
        if (null == clip) {
            return;
        }
        synchronized (this) {
            clip.stop();
            this.looping = false;
        }
    }

    @Override
    public final boolean isDone() {
        if (null == clip) {
            return false;
        }
        return !clip.isRunning();
    }

    private void wait(Supplier<Boolean> completed) {
        final int attempts = 5;
        for (int i = 0; i < attempts; i++) {
            if (completed.get() || !waitSoundSystemDelay()) {
                break;
            }
        }
    }

    private boolean waitSoundSystemDelay() {
        try {
            Thread.sleep(SoundSystem.DELAY);
            return true;
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            return false;
        }
    }

    private void lineStatusChanged(LineEvent line) {
        LineEvent.Type status = line.getType();
        this.started |= status == LineEvent.Type.START;
    }
}
