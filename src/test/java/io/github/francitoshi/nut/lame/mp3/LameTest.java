/*
 * Copyright (C) 2015-2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.github.francitoshi.nut.lame.mp3;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.BlockingQueue;
import org.junit.jupiter.api.Test;

public class LameTest
{
    static final String MP3 = ".mp3";
    /**
     * Test of main method, of class Jump3r.
     */
    @Test
    public void testMain() throws IOException, Exception
    {
        final URL path = LameTest.class.getResource("phone.wav");
        final File wav = new File(path.toString().replace("file:", ""));
        final File mp3 = new File(wav+MP3);

        Mp3Builder mp3bt = new Mp3Builder(false, mp3.getParentFile(), mp3.getName().replace(MP3,""), MP3, false, wav);
        BlockingQueue<File> queue = mp3bt.getWavQueue();
        for(int i=0;i<6;i++)
        {
            queue.put(new File(wav.getAbsolutePath().replace("phone", ""+i)));
        }
        queue.put(mp3);
        mp3bt.start();
        mp3bt.join();

        Mp3Builder mp3b = new Mp3Builder(false,  mp3.getParentFile(), mp3.getName().replace(MP3,""), MP3, false, wav, wav,wav,wav,wav);
        mp3b.start();
        mp3b.join();
    }

    
}
