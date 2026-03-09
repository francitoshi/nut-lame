/*
 * Mp3Builder.java
 *
 * Copyright (c) 2014-2026 francitoshi@gmail.com
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
 * Report bugs or new features to: francitoshi@gmail.com
 */
package io.nut.lame.mp3;

import io.nut.base.audio.speech.AudioBuilder;
import io.nut.base.io.IO;
import io.nut.lame.mp3.GetAudio.SoundFileFormat;

import java.io.*;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Mp3Builder extends AudioBuilder implements Runnable
{

    private volatile Lame lame;
    public int in_bitwidth = 16;
    public boolean in_signed = true;
    public ByteOrder in_endian = ByteOrder.LITTLE_ENDIAN;

    public Mp3Builder(boolean mute, File baseDir, String name, String ext, boolean overwrite, boolean deleteWav, int queueSize)
    {
        super(mute, baseDir, name, ext, overwrite, deleteWav, queueSize);
        this.lame = null;
    }
    public Mp3Builder(boolean mute, File dir, String name, String ext, boolean overwrite, File... wav)
    {
        this(mute, dir, name, ext, overwrite, false, wav.length+1);
        try
        {
            for(int i=0;i<wav.length;i++)
            {
                wavQueue.put(wav[i]);
            }
            wavQueue.put(dir);
        }
        catch (InterruptedException ex)
        {
            Logger.getLogger(Mp3Builder.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private File next()
    {
        if(wavQueue!=null)
        {
            try
            {
                return wavQueue.take();
            }
            catch (InterruptedException ex)
            {
                Logger.getLogger(Mp3Builder.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }

    private volatile Thread thread;
    public void start()
    {
        this.thread = new Thread(this);
        thread.start();
    }
    public void stop()
    {
        try
        {
            wavQueue.put(baseDir);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    public void join() throws InterruptedException
    {
        thread.join();
    }
    public void join(long millis) throws InterruptedException
    {
        thread.join(millis);
    }
    public void join(long millis, int nanos) throws InterruptedException
    {
        thread.join(millis, nanos);
    }

    public void run()
    {
        this.in_bitwidth = 16;
        this.in_signed = true;
        this.in_endian = ByteOrder.LITTLE_ENDIAN;
        this.lame = new Lame();

        File mp3 = overwrite ? new File(baseDir, name+ext) : IO.createNonExistentFile(baseDir, name, ext);

        System.out.println("--------------------------------------------------------------------------------------------");

        out.println("="+mp3);

        long t0 = System.nanoTime();

        this.lame.getParser().setInputFormat(SoundFileFormat.sf_wave);
        configureFlags(SoundFileFormat.sf_wave);
        FrameSkip enc = new FrameSkip();
        try
        {
            int ret = 0;
            mp3.getParentFile().mkdirs();
            FileOutputStream fout = new FileOutputStream(mp3);
            DataOutput outf = new DataOutputStream(new BufferedOutputStream(fout, 1 << 20));
            try
            {
                brhist_init_package();
                boolean first=true;
                for(File wav=next(); wav!=null && !wav.equals(baseDir); wav=next())
                {
                    out.println("+"+wav);
                    out.flush();
                    lame.getAudio().initInFile(lame.getFlags(), wav.toString(), enc);
                    lame.getFlags().setWriteId3tagAutomatic(false);
                    if(first)
                    {
                        first=false;
                        ret = lame.initParams();
                        if(ret<0)
                        {
                            break;
                        }
                    }
                    ret=lame_encoder(outf, mp3.toString());
                    if(ret<0)
                    {
                        break;
                    }
                    if(deleteWav)
                    {
                        wav.delete();
                    }
                }
            }
            finally
            {
                ((Closeable) outf).close();
            }
            RandomAccessFile rf = new RandomAccessFile(mp3, "rw");
            rf.seek(ret>0 ? ret : 0);
            write_xing_frame(rf);
            rf.close();
            
        }
        catch (FileNotFoundException ex)
        {
            err.printf("Can't init outfile '%s'\n", mp3.toString());
        }
        catch (IOException ex)
        {
            Logger.getLogger(Mp3Builder.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        lame.getAudio().close_infile(); /* close the input file */
        lame.close();
        
        long t1 = System.nanoTime();
        long diff= (t1-t0) / 1000000;
        out.println("="+diff+"ms");
        wavQueue.clear();
    }
    
    private int resample_rate(double freq)
    {
        if (freq >= 1.e3)
        {
            freq *= 1.e-3;
        }

        switch ((int) freq)
        {
            case 8:
                return 8000;
            case 11:
                return 11025;
            case 12:
                return 12000;
            case 16:
                return 16000;
            case 22:
                return 22050;
            case 24:
                return 24000;
            case 32:
                return 32000;
            case 44:
                return 44100;
            case 48:
                return 48000;
            default:
                err.printf("Illegal resample frequency: %.3f kHz\n", freq);
                return 0;
        }
    }
    
    public int configureFlags(GetAudio.SoundFileFormat sff)
    {
        final LameGlobalFlags gfp = lame.getFlags();
        final Parse parse = lame.getParser();
        final Presets pre = new Presets();
        pre.setModules(lame);
        
        /* set to 1 if we parse an input file name */
        int autoconvert = 0;

        int nogap = 0;
        /* set to 1 to use VBR tags in NOGAP mode */
        int nogap_tags = 0;
        final String programName = "lame";
        int count_nogap = 0;
        /* is RG explicitly disabled by the user */
        int noreplaygain = 0;
        Parse.ID3TAG_MODE id3tag_mode = null;//Parse.ID3TAG_MODE.ID3TAG_MODE_DEFAULT;

        /* turn on display options. user settings may turn them off below */
        parse.silent = 0;
        ////parse.ignore_tag_errors = false;
        parse.brhist = true;
        parse.mp3_delay = 0;
        parse.mp3_delay_set = false;
        parse.print_clipping_info = false;
        parse.disable_wav_header = false;
        parse.id3.init(gfp);

        double resample = 0;
        boolean vbrOld = false;
        boolean vbrNew = false;
        boolean vbrMtrh = false;
        boolean cbr = false;
        int abr = 0;
        boolean r3mix = false;
        int bitwidth = 0;
        boolean signed = false;
        boolean bigEndian = false;
        
        resample = 44;
        
        if(resample!=0)
        {
            gfp.setOutSampleRate(resample_rate(resample));
        }
        if(vbrOld)
        {
            gfp.setVBR(VbrMode.vbr_rh);
        }
        if(vbrNew)
        {
            gfp.setVBR(VbrMode.vbr_mtrh);
        }
        if(vbrMtrh)
        {
            gfp.setVBR(VbrMode.vbr_mtrh);
        }
        if(cbr)
        {
            gfp.setVBR(VbrMode.vbr_off);
        }
        if(abr!=0)
        {
            gfp.setVBR(VbrMode.vbr_abr);
            gfp.VBR_mean_bitrate_kbps = abr;
            /*
             * values larger than 8000 are bps (like Fraunhofer), so
             * it's strange to get 320000 bps MP3 when specifying
             * 8000 bps MP3
             */
            if (gfp.VBR_mean_bitrate_kbps >= 8000)
            {
                gfp.VBR_mean_bitrate_kbps = (gfp.VBR_mean_bitrate_kbps + 500) / 1000;
            }

            gfp.VBR_mean_bitrate_kbps = Math.min(gfp.VBR_mean_bitrate_kbps, 320);
            gfp.VBR_mean_bitrate_kbps = Math.max(gfp.VBR_mean_bitrate_kbps, 8);

        }
        if(r3mix)
        {
            gfp.preset = Lame.R3MIX;
            pre.apply_preset(gfp, Lame.R3MIX, 1);
        }
        if(bitwidth!=0)
        {
            in_bitwidth = bitwidth;
        }
        in_signed = signed;

        in_endian = bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;

        
        /* RG is enabled by default */
        if (0 == noreplaygain)
        {
            gfp.setFindReplayGain(true);
        }

        /* disable VBR tags with nogap unless the VBR tags are forced */
        if (nogap != 0 && gfp.bWriteVbrTag && nogap_tags == 0)
        {
            out.println("Note: Disabling VBR Xing/Info tag since it interferes with --nogap\n");
            gfp.bWriteVbrTag = false;
        }

        /* default guess for number of channels */
        if (autoconvert != 0)
        {
            gfp.setInNumChannels(2);
        }
        else if (MPEGMode.MONO == gfp.getMode())
        {
            gfp.setInNumChannels(1);
        }
        else
        {
            gfp.setInNumChannels(2);
        }

        if (gfp.free_format)
        {
            if (gfp.getBitRate() < 8 || gfp.getBitRate() > 640)
            {
                err.printf("For free format, specify a bitrate between 8 and 640 kbps\n");
                err.printf("with the -b <bitrate> option\n");
                return -1;
            }
        }
        return 0;
    }    
    private void brhist_init_package()
    {
        if (lame.getParser().brhist)
        {
            if (lame.getHist().brhist_init(lame.getFlags(), lame.getFlags().VBR_min_bitrate_kbps, lame.getFlags().VBR_max_bitrate_kbps) != 0)
            {
                lame.getParser().brhist = false; /* fail to initialize */
            }
        }
        else
        {
            lame.getHist().brhist_init(lame.getFlags(), 128, 128);/* Dirty hack */
        }
    }
    private int lame_encoder(final DataOutput outf, final String outPath)
    {
        byte mp3buffer[] = new byte[Lame.LAME_MAXMP3BUFFER];
        float Buffer[][] = new float[2][1152];
        int iread;

        int imp3 = lame.getId3().lame_get_id3v2_tag(lame.getFlags(), mp3buffer, mp3buffer.length);
        if (imp3 > mp3buffer.length)
        {
            err.printf("Error writing ID3v2 tag: buffer too small: buffer size=%d  ID3v2 size=%d\n",
                            mp3buffer.length, imp3);
            return 1;
        }
        try
        {
            outf.write(mp3buffer, 0, imp3);
        }
        catch (IOException e)
        {
            err.printf("Error writing ID3v2 tag \n");
            return 1;
        }
        int id3v2_size = imp3;

        /* encode until we hit eof */
        do
        {
            /* read in 'iread' samples */
            iread = lame.getAudio().get_audio(lame.getFlags(), Buffer);

            if (iread >= 0)
            {
                /* encode */
                imp3 = lame.encodeBuffer(Buffer[0], Buffer[1], iread, mp3buffer);

                /* was our output buffer big enough? */
                if (imp3 < 0)
                {
                    if (imp3 == -1)
                    {
                        err.printf("mp3 buffer is not big enough... \n");
                    }
                    else
                    {
                        err.printf("mp3 internal error:  error code=%d\n", imp3);
                    }
                    return 1;
                }

                try
                {
                    outf.write(mp3buffer, 0, imp3);
                }
                catch (IOException e)
                {
                    err.printf("Error writing mp3 output \n");
                    return 1;
                }
            }
        } while (iread > 0);

        /*
         * may return one more mp3 frame
         */
        imp3 = lame.encodeFlush(mp3buffer);
        /*
         * may return one more mp3 frame
         */

        if (imp3 < 0)
        {
            if (imp3 == -1)
            {
                err.printf("mp3 buffer is not big enough... \n");
            }
            else
            {
                err.printf("mp3 internal error:  error code=%d\n", imp3);
            }
            return 1;

        }

        try
        {
            outf.write(mp3buffer, 0, imp3);
        }
        catch (IOException e)
        {
            err.printf("Error writing mp3 output \n");
            return 1;
        }

        imp3 = lame.getId3().lame_get_id3v1_tag(lame.getFlags(), mp3buffer, mp3buffer.length);
        if (imp3 > mp3buffer.length)
        {
            err.printf("Error writing ID3v1 tag: buffer too small: buffer size=%d  ID3v1 size=%d\n",
                            mp3buffer.length, imp3);
        }
        else
        {
            if (imp3 > 0)
            {
                try
                {
                    outf.write(mp3buffer, 0, imp3);
                }
                catch (IOException e)
                {
                    err.printf("Error writing ID3v1 tag \n");
                    return 1;
                }
            }
        }
        return id3v2_size;
    }
    private int write_xing_frame(final RandomAccessFile outf)
    {
        byte mp3buffer[] = new byte[Lame.LAME_MAXMP3BUFFER];

        int imp3 = lame.getVbr().getLameTagFrame(lame.getFlags(), mp3buffer);
        if (imp3 > mp3buffer.length)
        {
            err.printf("Error writing LAME-tag frame: buffer too small: buffer size=%d  frame size=%d\n",
                            mp3buffer.length, imp3);
            return -1;
        }
        if (imp3 <= 0)
        {
            return 0;
        }
        try
        {
            outf.write(mp3buffer, 0, imp3);
        }
        catch (IOException e)
        {
            err.println("Error writing LAME-tag");
            return -1;
        }
        return imp3;
    }

    public BlockingQueue<File> getWavQueue()
    {
        return wavQueue;
    }

    public File getBaseDir()
    {
        return baseDir;
    }
}
