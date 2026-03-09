/*
 * Jump3rTest.java
 *
 * Copyright (c) 2015 Francisco Gómez Carrasco
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
 *
 * Report bugs or new features to: flikxxi@gmail.com
 */
package io.nut.lame.mp3;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.BlockingQueue;
import org.junit.Test;

public class LameTest
{
    

    static final String MP3 = ".mp3";
    /**
     * Test of main method, of class Jump3r.
     */
    @Test
    public void testMain() throws IOException, Exception
    {
        final URL path = Jump3rTest.class.getResource("phone.wav");
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
