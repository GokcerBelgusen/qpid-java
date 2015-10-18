/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.apache.qpid.bytebuffer;

import java.util.Collection;
import java.util.Iterator;

import junit.framework.TestCase;
import org.junit.Assert;

public class QpidByteBufferOutputStreamTest extends TestCase
{
    public void testWriteByteByByte() throws Exception
    {
        QpidByteBufferOutputStream stream = new QpidByteBufferOutputStream(false, 3);
        stream.write('a');
        stream.write('b');

        Collection<QpidByteBuffer> bufs = stream.fetchAccumulatedBuffers();
        assertEquals("Unexpected number of buffers", 2, bufs.size());
        Iterator<QpidByteBuffer> bufItr = bufs.iterator();

        QpidByteBuffer buf1 = bufItr.next();
        assertBufferContent("1st buffer", buf1, "a".getBytes());

        QpidByteBuffer buf2 = bufItr.next();
        assertBufferContent("2nd buffer", buf2, "b".getBytes());
    }

    public void testWriteByteArrays() throws Exception
    {
        QpidByteBufferOutputStream stream = new QpidByteBufferOutputStream(false, 8);
        stream.write("abcd".getBytes(), 0, 4);
        stream.write("_ef_".getBytes(), 1, 2);

        Collection<QpidByteBuffer> bufs = stream.fetchAccumulatedBuffers();
        assertEquals("Unexpected number of buffers", 2, bufs.size());
        Iterator<QpidByteBuffer> bufItr = bufs.iterator();

        QpidByteBuffer buf1 = bufItr.next();
        assertBufferContent("1st buffer", buf1, "abcd".getBytes());

        QpidByteBuffer buf2 = bufItr.next();
        assertBufferContent("2nd buffer", buf2, "ef".getBytes());
    }

    public void testWriteByteArrays_ArrayTooLargeForSingleBuffer() throws Exception
    {
        QpidByteBufferOutputStream stream = new QpidByteBufferOutputStream(false, 8);
        stream.write("abcdefghi".getBytes());

        Collection<QpidByteBuffer> bufs = stream.fetchAccumulatedBuffers();
        assertEquals("Unexpected number of buffers", 2, bufs.size());
        Iterator<QpidByteBuffer> bufItr = bufs.iterator();

        QpidByteBuffer buf1 = bufItr.next();
        assertBufferContent("1st buffer", buf1, "abcdefgh".getBytes());

        QpidByteBuffer buf2 = bufItr.next();
        assertBufferContent("2nd buffer", buf2, "i".getBytes());
    }

    private void assertBufferContent(String bufName, QpidByteBuffer buf, byte[] expectedBytes)
    {
        assertEquals(bufName + " has unexpected number of bytes", expectedBytes.length, buf.remaining());
        byte[] copy = new byte[buf.remaining()];
        buf.get(copy);
        Assert.assertArrayEquals(bufName + " has unexpected content", expectedBytes, copy);
    }

}