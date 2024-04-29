/*
 * Copyright (c) 1995, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.util.zip;

/*
 * This class defines the constants that are used by the classes
 * which manipulate Zip64 files.
 */

class ZipConstants64 {

    /*
     * ZIP64 constants
     */
    static final long ZIP64_ENDSIG = 0x06064b50L;  
    static final long ZIP64_LOCSIG = 0x07064b50L;  
    static final int  ZIP64_ENDHDR = 56;           
    static final int  ZIP64_LOCHDR = 20;           
    static final int  ZIP64_EXTHDR = 24;           
    static final int  ZIP64_EXTID  = 0x0001;       

    static final int  ZIP64_MAGICCOUNT = 0xFFFF;
    static final long ZIP64_MAGICVAL = 0xFFFFFFFFL;

    /*
     * Zip64 End of central directory (END) header field offsets
     */
    static final int  ZIP64_ENDLEN = 4;       
    static final int  ZIP64_ENDVEM = 12;      
    static final int  ZIP64_ENDVER = 14;      
    static final int  ZIP64_ENDNMD = 16;      
    static final int  ZIP64_ENDDSK = 20;      
    static final int  ZIP64_ENDTOD = 24;      
    static final int  ZIP64_ENDTOT = 32;      
    static final int  ZIP64_ENDSIZ = 40;      
    static final int  ZIP64_ENDOFF = 48;      
    static final int  ZIP64_ENDEXT = 56;      

    /*
     * Zip64 End of central directory locator field offsets
     */
    static final int  ZIP64_LOCDSK = 4;       
    static final int  ZIP64_LOCOFF = 8;       
    static final int  ZIP64_LOCTOT = 16;      

    /*
     * Zip64 Extra local (EXT) header field offsets
     */
    static final int  ZIP64_EXTCRC = 4;       
    static final int  ZIP64_EXTSIZ = 8;       
    static final int  ZIP64_EXTLEN = 16;      

    /*
     * Language encoding flag (general purpose flag bit 11)
     *
     * If this bit is set the filename and comment fields for this
     * entry must be encoded using UTF-8.
     */
    static final int USE_UTF8 = 0x800;

    /*
     * Constants below are defined here (instead of in ZipConstants)
     * to avoid being exposed as public fields of ZipFile, ZipEntry,
     * ZipInputStream and ZipOutputstream.
     */

    /*
     * Extra field header ID
     */
    static final int  EXTID_ZIP64 = 0x0001;    
    static final int  EXTID_NTFS  = 0x000a;    
    static final int  EXTID_UNIX  = 0x000d;    
    static final int  EXTID_EXTT  = 0x5455;    

    /*
     * EXTT timestamp flags
     */
    static final int  EXTT_FLAG_LMT = 0x1;       
    static final int  EXTT_FLAG_LAT = 0x2;       
    static final int  EXTT_FLAT_CT  = 0x4;       

    private ZipConstants64() {}
}