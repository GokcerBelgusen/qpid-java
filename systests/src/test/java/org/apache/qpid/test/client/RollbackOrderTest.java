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
package org.apache.qpid.test.client;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Session;

import junit.framework.AssertionFailedError;

import org.apache.qpid.test.utils.QpidBrokerTestCase;

/**
 * RollbackOrderTest, QPID-1864, QPID-1871
 *
 * Description:
 *
 * The problem that this test is exposing is that the dispatcher used to be capable
 * of holding on to a message when stopped. This meant that when the rollback was
 * called and the dispatcher stopped it may have hold of a message. So after all
 * the local queues(preDeliveryQueue, SynchronousQueue, PostDeliveryTagQueue)
 * have been cleared the client still had a single message, the one the
 * dispatcher was holding on to.
 *
 * As a result the TxRollback operation would run and then release the dispatcher.
 * Whilst the dispatcher would then proceed to reject the message it was holding
 * the Broker would already have resent that message so the rejection would silently
 * fail.
 *
 * And the client would receive that single message 'early', depending on the
 * number of messages already received when rollback was called.
 *
 *
 * Aims:
 *
 * The tests puts 50 messages on to the queue.
 *
 * The test then tries to cause the dispatcher to stop whilst it is in the process
 * of moving a message from the preDeliveryQueue to a consumers sychronousQueue.
 *
 * To exercise this path we have 50 message flowing to the client to give the
 * dispatcher a bit of work to do moving messages.
 *
 * Then we loop - 10 times
 *  - Validating that the first message received is always message 1.
 *  - Receive a few more so that there are a few messages to reject.
 *  - call rollback, to try and catch the dispatcher mid process.
 *
 * Outcome:
 *
 * The hope is that we catch the dispatcher mid process and cause a BasicReject
 * to fail. Which will be indicated in the log but will also cause that failed
 * rejected message to be the next to be delivered which will not be message 1
 * as expected.
 *
 * We are testing a race condition here but we can check through the log file if
 * the race condition occurred. However, performing that check will only validate
 * the problem exists and will not be suitable as part of a system test.
 *
 * @see org.apache.qpid.test.unit.transacted.CommitRollbackTest
 */
public class RollbackOrderTest extends QpidBrokerTestCase
{

    private Connection _connection;
    private Queue _queue;
    private Session _session;
    private MessageConsumer _consumer;

    @Override public void setUp() throws Exception
    {
        super.setUp();
        _connection = getConnection();

        _session = _connection.createSession(true, Session.SESSION_TRANSACTED);
        _queue = createTestQueue(_session);
        _consumer = _session.createConsumer(_queue);

        //Send more messages so it is more likely that the dispatcher is
        // processing on rollback.
        sendMessage(_session, _queue, 50);
        _session.commit();

    }

    public void testOrderingAfterRollback() throws Exception
    {
        //Start the session now so we
        _connection.start();

        for (int i = 0; i < 20; i++)
        {
            Message msg = _consumer.receive();
            assertEquals("Incorrect Message Received", 0, msg.getIntProperty(INDEX));

            // Pull additional messages through so we have some reject work to do
            for (int m=1; m <= 5 ; m++)
            {
                msg = _consumer.receive();
                assertEquals("Incorrect Message Received (message " + m + ")", m, msg.getIntProperty(INDEX));
            }

            _session.rollback();
        }
    }

    public void testOrderingAfterRollbackOnMessage() throws Exception
    {
        final CountDownLatch count= new CountDownLatch(20);
        final Exception exceptions[] = new Exception[20];
        final AtomicBoolean failed = new AtomicBoolean(false);

        _consumer.setMessageListener(new MessageListener()
        {

            public void onMessage(Message message)
            {

                Message msg = message;
                try
                {
                    count.countDown();
                    assertEquals("Incorrect Message Received", 0, msg.getIntProperty(INDEX));

                    _session.rollback();
                }
                catch (JMSException e)
                {
                    _logger.error("Error:" + e.getMessage(), e);
                    exceptions[(int)count.getCount()] = e;
                }
                catch (AssertionFailedError cf)
                {
                    // End Test if Equality test fails
                    while (count.getCount() != 0)
                    {
                        count.countDown();
                    }

                    _logger.error("Error:" + cf.getMessage(), cf);
                    failed.set(true);
                }
            }
        });
        //Start the session now so we
        _connection.start();

        count.await(10l, TimeUnit.SECONDS);
        assertEquals("Not all message received.  Count should be 0.", 0, count.getCount());

        for (Exception e : exceptions)
        {
            if (e != null)
            {
                _logger.error("Encountered exception", e);
                failed.set(true);
            }
        }

        _connection.close();

        assertFalse("Exceptions thrown during test run, Check Std.err.", failed.get());
    }
}
