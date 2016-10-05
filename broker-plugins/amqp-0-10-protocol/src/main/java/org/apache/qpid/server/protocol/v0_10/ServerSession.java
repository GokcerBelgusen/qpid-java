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
package org.apache.qpid.server.protocol.v0_10;

import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.CHANNEL_FORMAT;
import static org.apache.qpid.util.Serial.gt;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.security.auth.Subject;

import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.connection.SessionPrincipal;
import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.consumer.ConsumerTarget;
import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.messages.ChannelMessages;
import org.apache.qpid.server.logging.subjects.ChannelLogSubject;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageDestination;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Consumer;
import org.apache.qpid.server.model.NamedAddressSpace;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.protocol.CapacityChecker;
import org.apache.qpid.server.protocol.ConsumerListener;
import org.apache.qpid.server.security.SecurityToken;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.txn.AlreadyKnownDtxException;
import org.apache.qpid.server.txn.AsyncAutoCommitTransaction;
import org.apache.qpid.server.txn.DistributedTransaction;
import org.apache.qpid.server.txn.DtxNotSelectedException;
import org.apache.qpid.server.txn.IncorrectDtxStateException;
import org.apache.qpid.server.txn.JoinAndResumeDtxException;
import org.apache.qpid.server.txn.LocalTransaction;
import org.apache.qpid.server.txn.NotAssociatedDtxException;
import org.apache.qpid.server.txn.RollbackOnlyDtxException;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.txn.SuspendAndFailDtxException;
import org.apache.qpid.server.txn.TimeoutDtxException;
import org.apache.qpid.server.txn.UnknownDtxBranchException;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.Deletable;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.transport.Binary;
import org.apache.qpid.transport.Connection;
import org.apache.qpid.transport.MessageCreditUnit;
import org.apache.qpid.transport.MessageFlow;
import org.apache.qpid.transport.MessageFlowMode;
import org.apache.qpid.transport.MessageSetFlowMode;
import org.apache.qpid.transport.MessageStop;
import org.apache.qpid.transport.MessageTransfer;
import org.apache.qpid.transport.Method;
import org.apache.qpid.transport.Range;
import org.apache.qpid.transport.RangeSet;
import org.apache.qpid.transport.RangeSetFactory;
import org.apache.qpid.transport.Session;
import org.apache.qpid.transport.SessionDelegate;
import org.apache.qpid.transport.Xid;
import org.apache.qpid.transport.network.Ticker;

public class ServerSession extends Session
        implements AMQSessionModel<ServerSession>, LogSubject, AsyncAutoCommitTransaction.FutureRecorder,
                   Deletable<ServerSession>

{
    private static final Logger _logger = LoggerFactory.getLogger(ServerSession.class);

    private static final String NULL_DESTINATION = UUID.randomUUID().toString();
    private static final int PRODUCER_CREDIT_TOPUP_THRESHOLD = 1 << 30;
    private static final int UNFINISHED_COMMAND_QUEUE_THRESHOLD = 500;

    private final UUID _id = UUID.randomUUID();
    private final Subject _subject = new Subject();
    private final AccessControlContext _accessControllerContext;
    private final SecurityToken _token;
    private long _createTime = System.currentTimeMillis();

    private final Set<Object> _blockingEntities = Collections.synchronizedSet(new HashSet<Object>());

    private final AtomicBoolean _blocking = new AtomicBoolean(false);
    private ChannelLogSubject _logSubject;
    private final AtomicInteger _outstandingCredit = new AtomicInteger(UNLIMITED_CREDIT);
    private final CheckCapacityAction _checkCapacityAction = new CheckCapacityAction();
    private final CopyOnWriteArrayList<ConsumerListener> _consumerListeners = new CopyOnWriteArrayList<ConsumerListener>();
    private final ConfigurationChangeListener _consumerClosedListener = new ConsumerClosedListener();
    private org.apache.qpid.server.model.Session<?> _modelObject;
    private long _blockTime;
    private long _blockingTimeout;
    private boolean _wireBlockingState;
    private final List<ConsumerTarget> _consumersWithPendingWork = new ArrayList<>();

    public SecurityToken getToken()
    {
        return _token;
    }

    public static interface MessageDispositionChangeListener
    {
        public void onAccept();

        public void onRelease(boolean setRedelivered);

        public void onReject();

        public boolean acquire();


    }

    private final SortedMap<Integer, MessageDispositionChangeListener> _messageDispositionListenerMap =
            new ConcurrentSkipListMap<Integer, MessageDispositionChangeListener>();

    private ServerTransaction _transaction;

    private final AtomicLong _txnStarts = new AtomicLong(0);
    private final AtomicLong _txnCommits = new AtomicLong(0);
    private final AtomicLong _txnRejects = new AtomicLong(0);
    private final AtomicLong _txnCount = new AtomicLong(0);

    private Map<String, ConsumerTarget_0_10> _subscriptions = new ConcurrentHashMap<String, ConsumerTarget_0_10>();
    private final CopyOnWriteArrayList<Consumer<?>> _consumers = new CopyOnWriteArrayList<Consumer<?>>();

    private final List<Action<? super ServerSession>> _taskList = new CopyOnWriteArrayList<Action<? super ServerSession>>();

    private AtomicReference<LogMessage> _forcedCloseLogMessage = new AtomicReference<LogMessage>();

    private volatile long _uncommittedMessageSize;
    private final List<StoredMessage<MessageMetaData_0_10>> _uncommittedMessages = new ArrayList<>();
    private long _maxUncommittedInMemorySize;


    public ServerSession(Connection connection, SessionDelegate delegate, Binary name, long expiry)
    {
        super(connection, delegate, name, expiry);
        _transaction = new AsyncAutoCommitTransaction(this.getMessageStore(),this);
        _logSubject = new ChannelLogSubject(this);

        ServerConnection serverConnection = (ServerConnection) connection;
        AMQPConnection_0_10 amqpConnection = serverConnection.getAmqpConnection();

        _subject.getPrincipals().addAll(serverConnection.getAuthorizedSubject().getPrincipals());
        _subject.getPrincipals().add(new SessionPrincipal(this));
        _accessControllerContext = amqpConnection.getAccessControlContextFromSubject(_subject);
        final NamedAddressSpace addressSpace = serverConnection.getAddressSpace();

        if(addressSpace instanceof ConfiguredObject)
        {
            _token = ((ConfiguredObject)addressSpace).newToken(_subject);
        }
        else
        {
            _token = amqpConnection.getBroker().newToken(_subject);
        }

        _blockingTimeout = serverConnection.getBroker().getContextValue(Long.class,
                                                                                       Broker.CHANNEL_FLOW_CONTROL_ENFORCEMENT_TIMEOUT);
        _maxUncommittedInMemorySize = getConnection().getAmqpConnection().getContextProvider().getContextValue(Long.class, org.apache.qpid.server.model.Connection.MAX_UNCOMMITTED_IN_MEMORY_SIZE);

    }

    public AccessControlContext getAccessControllerContext()
    {
        return _accessControllerContext;
    }

    protected void setState(final State state)
    {
        if(runningAsSubject())
        {
            super.setState(state);

            if (state == State.OPEN)
            {
                getConnection().getAmqpConnection().getEventLogger().message(ChannelMessages.CREATE());
            }
        }
        else
        {
            runAsSubject(new PrivilegedAction<Void>() {

                @Override
                public Void run()
                {
                    setState(state);
                    return null;
                }
            });

        }
    }

    private <T> T runAsSubject(final PrivilegedAction<T> privilegedAction)
    {
        return AccessController.doPrivileged(privilegedAction, getAccessControllerContext());
    }

    private boolean runningAsSubject()
    {
        return getAuthorizedSubject().equals(Subject.getSubject(AccessController.getContext()));
    }

    private void invokeBlock()
    {
        invoke(new MessageSetFlowMode("", MessageFlowMode.CREDIT));
        invoke(new MessageStop(""));
    }

    private void invokeUnblock()
    {
        MessageFlow mf = new MessageFlow();
        mf.setUnit(MessageCreditUnit.MESSAGE);
        mf.setDestination("");
        _outstandingCredit.set(Integer.MAX_VALUE);
        mf.setValue(Integer.MAX_VALUE);
        invoke(mf);
    }


    @Override
    protected boolean isFull(int id)
    {
        return isCommandsFull(id);
    }

    public int enqueue(final MessageTransferMessage message,
                       final InstanceProperties instanceProperties,
                       final MessageDestination exchange)
    {
        if(_outstandingCredit.get() != UNLIMITED_CREDIT
                && _outstandingCredit.decrementAndGet() == (Integer.MAX_VALUE - PRODUCER_CREDIT_TOPUP_THRESHOLD))
        {
            _outstandingCredit.addAndGet(PRODUCER_CREDIT_TOPUP_THRESHOLD);
            invoke(new MessageFlow("",MessageCreditUnit.MESSAGE, PRODUCER_CREDIT_TOPUP_THRESHOLD));
        }
        int enqueues = exchange.send(message,
                                     message.getInitialRoutingAddress(),
                                     instanceProperties, _transaction, _checkCapacityAction
                                    );
        getAMQPConnection().registerMessageReceived(message.getSize(), message.getArrivalTime());
        incrementOutstandingTxnsIfNecessary();
        incrementUncommittedMessageSize(message.getStoredMessage());
        return enqueues;
    }

    private void resetUncommittedMessages()
    {
        _uncommittedMessageSize = 0l;
        _uncommittedMessages.clear();
    }

    private void incrementUncommittedMessageSize(final StoredMessage<MessageMetaData_0_10> handle)
    {
        if (isTransactional() && !(_transaction instanceof DistributedTransaction))
        {
            _uncommittedMessageSize += handle.getMetaData().getContentSize();
            if (_uncommittedMessageSize > getMaxUncommittedInMemorySize())
            {
                handle.flowToDisk();
                if(!_uncommittedMessages.isEmpty() || _uncommittedMessageSize == handle.getMetaData().getContentSize())
                {
                    getConnection().getAmqpConnection().getEventLogger()
                            .message(_logSubject, ChannelMessages.LARGE_TRANSACTION_WARN(_uncommittedMessageSize));
                }

                if(!_uncommittedMessages.isEmpty())
                {
                    for (StoredMessage<MessageMetaData_0_10> uncommittedHandle : _uncommittedMessages)
                    {
                        uncommittedHandle.flowToDisk();
                    }
                    _uncommittedMessages.clear();
                }
            }
            else
            {
                _uncommittedMessages.add(handle);
            }
        }
    }


    public void sendMessage(MessageTransfer xfr,
                            Runnable postIdSettingAction)
    {
        getAMQPConnection().registerMessageDelivered(xfr.getBodySize());
        invoke(xfr, postIdSettingAction);
    }

    public void onMessageDispositionChange(MessageTransfer xfr, MessageDispositionChangeListener acceptListener)
    {
        _messageDispositionListenerMap.put(xfr.getId(), acceptListener);
    }


    private static interface MessageDispositionAction
    {
        void performAction(MessageDispositionChangeListener  listener);
    }

    public void accept(RangeSet ranges)
    {
        dispositionChange(ranges, new MessageDispositionAction()
        {
            public void performAction(MessageDispositionChangeListener listener)
            {
                listener.onAccept();
            }
        });
    }


    public void release(RangeSet ranges, final boolean setRedelivered)
    {
        dispositionChange(ranges, new MessageDispositionAction()
                                      {
                                          public void performAction(MessageDispositionChangeListener listener)
                                          {
                                              listener.onRelease(setRedelivered);
                                          }
                                      });
    }

    public void reject(RangeSet ranges)
    {
        dispositionChange(ranges, new MessageDispositionAction()
                                      {
                                          public void performAction(MessageDispositionChangeListener listener)
                                          {
                                              listener.onReject();
                                          }
                                      });
    }

    public RangeSet acquire(RangeSet transfers)
    {
        RangeSet acquired = RangeSetFactory.createRangeSet();

        if(!_messageDispositionListenerMap.isEmpty())
        {
            Iterator<Integer> unacceptedMessages = _messageDispositionListenerMap.keySet().iterator();
            Iterator<Range> rangeIter = transfers.iterator();

            if(rangeIter.hasNext())
            {
                Range range = rangeIter.next();

                while(range != null && unacceptedMessages.hasNext())
                {
                    int next = unacceptedMessages.next();
                    while(gt(next, range.getUpper()))
                    {
                        if(rangeIter.hasNext())
                        {
                            range = rangeIter.next();
                        }
                        else
                        {
                            range = null;
                            break;
                        }
                    }
                    if(range != null && range.includes(next))
                    {
                        MessageDispositionChangeListener changeListener = _messageDispositionListenerMap.get(next);
                        if(changeListener != null && changeListener.acquire())
                        {
                            acquired.add(next);
                        }
                    }


                }

            }


        }

        return acquired;
    }

    public void dispositionChange(RangeSet ranges, MessageDispositionAction action)
    {
        if(ranges != null)
        {

            if(ranges.size() == 1)
            {
                Range r = ranges.getFirst();
                for(int i = r.getLower(); i <= r.getUpper(); i++)
                {
                    MessageDispositionChangeListener changeListener = _messageDispositionListenerMap.remove(i);
                    if(changeListener != null)
                    {
                        action.performAction(changeListener);
                    }
                }
            }
            else if(!_messageDispositionListenerMap.isEmpty())
            {
                Iterator<Integer> unacceptedMessages = _messageDispositionListenerMap.keySet().iterator();
                Iterator<Range> rangeIter = ranges.iterator();

                if(rangeIter.hasNext())
                {
                    Range range = rangeIter.next();

                    while(range != null && unacceptedMessages.hasNext())
                    {
                        int next = unacceptedMessages.next();
                        while(gt(next, range.getUpper()))
                        {
                            if(rangeIter.hasNext())
                            {
                                range = rangeIter.next();
                            }
                            else
                            {
                                range = null;
                                break;
                            }
                        }
                        if(range != null && range.includes(next))
                        {
                            MessageDispositionChangeListener changeListener = _messageDispositionListenerMap.remove(next);
                            action.performAction(changeListener);
                        }


                    }

                }
            }
        }
    }

    public void removeDispositionListener(Method method)
    {
        _messageDispositionListenerMap.remove(method.getId());
    }

    public void onClose()
    {
        if(_transaction instanceof LocalTransaction)
        {
            _transaction.rollback();
        }
        else if(_transaction instanceof DistributedTransaction)
        {
            getAddressSpace().getDtxRegistry().endAssociations(this);
        }

        for(MessageDispositionChangeListener listener : _messageDispositionListenerMap.values())
        {
            listener.onRelease(true);
        }
        _messageDispositionListenerMap.clear();

        for (Action<? super ServerSession> task : _taskList)
        {
            task.performAction(this);
        }

        LogMessage operationalLoggingMessage = _forcedCloseLogMessage.get();
        if (operationalLoggingMessage == null)
        {
            operationalLoggingMessage = ChannelMessages.CLOSE();
        }
        getConnection().getAmqpConnection().getEventLogger().message(getLogSubject(), operationalLoggingMessage);
    }

    @Override
    protected void awaitClose()
    {
        // Broker shouldn't block awaiting close - thus do override this method to do nothing
    }

    public void acknowledge(final ConsumerImpl consumer,
                            final ConsumerTarget_0_10 target,
                            final MessageInstance entry)
    {
        if (entry.makeAcquisitionUnstealable(consumer))
        {
            _transaction.dequeue(entry.getEnqueueRecord(),
                                 new ServerTransaction.Action()
                                 {

                                     public void postCommit()
                                     {
                                         entry.delete();
                                     }

                                     public void onRollback()
                                     {
                                         // The client has acknowledge the message and therefore have seen it.
                                         // In the event of rollback, the message must be marked as redelivered.
                                         entry.setRedelivered();
                                         entry.release(consumer);
                                     }
                                 });
        }
    }

    Collection<ConsumerTarget_0_10> getSubscriptions()
    {
        return _subscriptions.values();
    }

    public void register(String destination, ConsumerTarget_0_10 sub)
    {
        _subscriptions.put(destination == null ? NULL_DESTINATION : destination, sub);
    }


    public void register(final ConsumerImpl consumerImpl)
    {
        if(consumerImpl instanceof Consumer<?>)
        {
            final Consumer<?> consumer = (Consumer<?>) consumerImpl;
            _consumers.add(consumer);
            consumer.addChangeListener(_consumerClosedListener);
            consumerAdded(consumer);
        }
    }

    public ConsumerTarget_0_10 getSubscription(String destination)
    {
        return _subscriptions.get(destination == null ? NULL_DESTINATION : destination);
    }

    public void unregister(ConsumerTarget_0_10 sub)
    {
        _subscriptions.remove(sub.getName());
        sub.close();

    }

    public boolean isTransactional()
    {
        return _transaction.isTransactional();
    }

    public void selectTx()
    {
        _transaction = new LocalTransaction(this.getMessageStore());
        _txnStarts.incrementAndGet();
    }

    public void selectDtx()
    {
        _transaction = new DistributedTransaction(this, getAddressSpace().getDtxRegistry());

    }


    public void startDtx(Xid xid, boolean join, boolean resume)
            throws JoinAndResumeDtxException,
                   UnknownDtxBranchException,
                   AlreadyKnownDtxException,
                   DtxNotSelectedException
    {
        DistributedTransaction distributedTransaction = assertDtxTransaction();
        distributedTransaction.start(xid, join, resume);
    }


    public void endDtx(Xid xid, boolean fail, boolean suspend)
            throws NotAssociatedDtxException,
            UnknownDtxBranchException,
            DtxNotSelectedException,
            SuspendAndFailDtxException, TimeoutDtxException
    {
        DistributedTransaction distributedTransaction = assertDtxTransaction();
        distributedTransaction.end(xid, fail, suspend);
    }


    public long getTimeoutDtx(Xid xid)
            throws UnknownDtxBranchException
    {
        return getAddressSpace().getDtxRegistry().getTimeout(xid);
    }


    public void setTimeoutDtx(Xid xid, long timeout)
            throws UnknownDtxBranchException
    {
        getAddressSpace().getDtxRegistry().setTimeout(xid, timeout);
    }


    public void prepareDtx(Xid xid)
            throws UnknownDtxBranchException,
            IncorrectDtxStateException, StoreException, RollbackOnlyDtxException, TimeoutDtxException
    {
        getAddressSpace().getDtxRegistry().prepare(xid);
    }

    public void commitDtx(Xid xid, boolean onePhase)
            throws UnknownDtxBranchException,
            IncorrectDtxStateException, StoreException, RollbackOnlyDtxException, TimeoutDtxException
    {
        getAddressSpace().getDtxRegistry().commit(xid, onePhase);
    }


    public void rollbackDtx(Xid xid)
            throws UnknownDtxBranchException,
            IncorrectDtxStateException, StoreException, TimeoutDtxException
    {
        getAddressSpace().getDtxRegistry().rollback(xid);
    }


    public void forgetDtx(Xid xid) throws UnknownDtxBranchException, IncorrectDtxStateException
    {
        getAddressSpace().getDtxRegistry().forget(xid);
    }

    public List<Xid> recoverDtx()
    {
        return getAddressSpace().getDtxRegistry().recover();
    }

    private DistributedTransaction assertDtxTransaction() throws DtxNotSelectedException
    {
        if(_transaction instanceof DistributedTransaction)
        {
            return (DistributedTransaction) _transaction;
        }
        else
        {
            throw new DtxNotSelectedException();
        }
    }


    public void commit()
    {
        _transaction.commit();

        _txnCommits.incrementAndGet();
        _txnStarts.incrementAndGet();
        decrementOutstandingTxnsIfNecessary();
        resetUncommittedMessages();
    }

    public void rollback()
    {
        _transaction.rollback();

        _txnRejects.incrementAndGet();
        _txnStarts.incrementAndGet();
        decrementOutstandingTxnsIfNecessary();
        resetUncommittedMessages();
    }


    private void incrementOutstandingTxnsIfNecessary()
    {
        if(isTransactional())
        {
            //There can currently only be at most one outstanding transaction
            //due to only having LocalTransaction support. Set value to 1 if 0.
            _txnCount.compareAndSet(0,1);
        }
    }

    private void decrementOutstandingTxnsIfNecessary()
    {
        if(isTransactional())
        {
            //There can currently only be at most one outstanding transaction
            //due to only having LocalTransaction support. Set value to 0 if 1.
            _txnCount.compareAndSet(1,0);
        }
    }

    public Long getTxnCommits()
    {
        return _txnCommits.get();
    }

    public Long getTxnRejects()
    {
        return _txnRejects.get();
    }

    public int getChannelId()
    {
        return getChannel();
    }

    public Long getTxnCount()
    {
        return _txnCount.get();
    }

    public Long getTxnStart()
    {
        return _txnStarts.get();
    }

    public Principal getAuthorizedPrincipal()
    {
        return getConnection().getAuthorizedPrincipal();
    }

    public Subject getAuthorizedSubject()
    {
        return _subject;
    }

    public void addDeleteTask(Action<? super ServerSession> task)
    {
        _taskList.add(task);
    }

    public void removeDeleteTask(Action<? super ServerSession> task)
    {
        _taskList.remove(task);
    }

    public Object getReference()
    {
        return getConnection().getReference();
    }

    public MessageStore getMessageStore()
    {
        return getAddressSpace().getMessageStore();
    }

    public NamedAddressSpace getAddressSpace()
    {
        return getConnection().getAddressSpace();
    }

    public boolean isDurable()
    {
        return false;
    }


    public long getCreateTime()
    {
        return _createTime;
    }

    @Override
    public UUID getId()
    {
        return _id;
    }

    @Override
    public AMQPConnection_0_10 getAMQPConnection()
    {
        return getConnection().getAmqpConnection();
    }

    @Override
    public ServerConnection getConnection()
    {
        return (ServerConnection) super.getConnection();
    }


    public LogSubject getLogSubject()
    {
        return this;
    }

    public void block(Queue<?> queue)
    {
        block(queue, queue.getName());
    }

    public void block()
    {
        block(this, "** All Queues **");
    }


    private void block(final Object queue, final String name)
    {
        synchronized (_blockingEntities)
        {
            if(_blockingEntities.add(queue))
            {

                if(_blocking.compareAndSet(false,true))
                {
                    getConnection().getAmqpConnection().getEventLogger().message(_logSubject, ChannelMessages.FLOW_ENFORCED(name));
                    if(getState() == State.OPEN)
                    {
                        getConnection().notifyWork();
                    }
                }


            }
        }
    }

    public void unblock(Queue<?> queue)
    {
        unblock((Object)queue);
    }

    public void unblock()
    {
        unblock(this);
    }

    private void unblock(final Object queue)
    {
        if(_blockingEntities.remove(queue) && _blockingEntities.isEmpty())
        {
            if(_blocking.compareAndSet(true,false) && !isClosing())
            {
                getConnection().getAmqpConnection().getEventLogger().message(_logSubject, ChannelMessages.FLOW_REMOVED());
                getConnection().notifyWork();
            }
        }
    }


    boolean blockingTimeoutExceeded()
    {
        long blockTime = _blockTime;
        boolean b = _wireBlockingState && blockTime != 0 && (System.currentTimeMillis() - blockTime) > _blockingTimeout;
        return b;
    }

    @Override
    public void transportStateChanged()
    {
        for(ConsumerTarget_0_10 consumerTarget : getSubscriptions())
        {
            consumerTarget.transportStateChanged();
        }
    }

    @Override
    public Object getConnectionReference()
    {
        return getConnection().getReference();
    }

    public String toLogString()
    {
        long connectionId = super.getConnection() instanceof ServerConnection
                            ? getConnection().getConnectionId()
                            : -1;
        String authorizedPrincipal = (getAuthorizedPrincipal() == null) ? "?" : getAuthorizedPrincipal().getName();

        String remoteAddress = String.valueOf(getConnection().getRemoteSocketAddress());
        return "[" +
               MessageFormat.format(CHANNEL_FORMAT,
                                    connectionId,
                                    authorizedPrincipal,
                                    remoteAddress,
                                    getAddressSpace().getName(),
                                    getChannel())
            + "] ";
    }

    @Override
    public void close(AMQConstant cause, String message)
    {
        if (cause == null)
        {
            close();
        }
        else
        {
            close(cause.getCode(), message);
        }
    }

    void close(int cause, String message)
    {
        _forcedCloseLogMessage.compareAndSet(null, ChannelMessages.CLOSE_FORCED(cause, message));
        close();
    }

    @Override
    public void close()
    {
        // unregister subscriptions in order to prevent sending of new messages
        // to subscriptions with closing session
        unregisterSubscriptions();
        if(_modelObject != null)
        {
            _modelObject.delete();
        }
        super.close();
    }

    void unregisterSubscriptions()
    {
        final Collection<ConsumerTarget_0_10> subscriptions = getSubscriptions();
        for (ConsumerTarget_0_10 subscription_0_10 : subscriptions)
        {
            unregister(subscription_0_10);
        }
    }

    void stopSubscriptions()
    {
        final Collection<ConsumerTarget_0_10> subscriptions = getSubscriptions();
        for (ConsumerTarget_0_10 subscription_0_10 : subscriptions)
        {
            subscription_0_10.stop();
        }
    }


    public void receivedComplete()
    {
        final Collection<ConsumerTarget_0_10> subscriptions = getSubscriptions();
        for (ConsumerTarget_0_10 subscription_0_10 : subscriptions)
        {
            subscription_0_10.flushCreditState(false);
        }
        awaitCommandCompletion();
    }

    public int getUnacknowledgedMessageCount()
    {
        return _messageDispositionListenerMap.size();
    }

    public boolean getBlocking()
    {
        return _blocking.get();
    }

    private final LinkedList<AsyncCommand> _unfinishedCommandsQueue = new LinkedList<AsyncCommand>();

    public void completeAsyncCommands()
    {
        AsyncCommand cmd;
        while((cmd = _unfinishedCommandsQueue.peek()) != null && cmd.isReadyForCompletion())
        {
            cmd.complete();
            _unfinishedCommandsQueue.poll();
        }
        while(_unfinishedCommandsQueue.size() > UNFINISHED_COMMAND_QUEUE_THRESHOLD)
        {
            cmd = _unfinishedCommandsQueue.poll();
            cmd.complete();
        }
    }


    public void awaitCommandCompletion()
    {
        AsyncCommand cmd;
        while((cmd = _unfinishedCommandsQueue.poll()) != null)
        {
            cmd.complete();
        }
    }


    public Object getAsyncCommandMark()
    {
        return _unfinishedCommandsQueue.isEmpty() ? null : _unfinishedCommandsQueue.getLast();
    }

    public void recordFuture(final ListenableFuture<Void> future, final ServerTransaction.Action action)
    {
        _unfinishedCommandsQueue.add(new AsyncCommand(future, action));
    }

    private static class AsyncCommand
    {
        private final ListenableFuture<Void> _future;
        private ServerTransaction.Action _action;

        public AsyncCommand(final ListenableFuture<Void> future, final ServerTransaction.Action action)
        {
            _future = future;
            _action = action;
        }

        void complete()
        {
            boolean interrupted = false;
            try
            {
                while (true)
                {
                    try
                    {
                        _future.get();
                        break;
                    }
                    catch (InterruptedException e)
                    {
                        interrupted = true;
                    }

                }
            }
            catch(ExecutionException e)
            {
                if(e.getCause() instanceof RuntimeException)
                {
                    throw (RuntimeException)e.getCause();
                }
                else if(e.getCause() instanceof Error)
                {
                    throw (Error) e.getCause();
                }
                else
                {
                    throw new ServerScopedRuntimeException(e.getCause());
                }
            }
            if(interrupted)
            {
                Thread.currentThread().interrupt();
            }
            _action.postCommit();
            _action = null;
        }

        boolean isReadyForCompletion()
        {
            return _future.isDone();
        }
    }

    protected void setClose(boolean close)
    {
        super.setClose(close);
    }

    @Override
    public int getConsumerCount()
    {
        return _subscriptions.values().size();
    }

    @Override
    public Collection<Consumer<?>> getConsumers()
    {

        return Collections.unmodifiableCollection(_consumers);
    }

    @Override
    public void addConsumerListener(final ConsumerListener listener)
    {
        _consumerListeners.add(listener);
    }

    @Override
    public void removeConsumerListener(final ConsumerListener listener)
    {
        _consumerListeners.remove(listener);
    }

    @Override
    public void setModelObject(final org.apache.qpid.server.model.Session<?> session)
    {
        _modelObject = session;
    }

    @Override
    public org.apache.qpid.server.model.Session<?> getModelObject()
    {
        return _modelObject;
    }

    @Override
    public long getTransactionStartTime()
    {
        ServerTransaction serverTransaction = _transaction;
        if (serverTransaction.isTransactional())
        {
            return serverTransaction.getTransactionStartTime();
        }
        else
        {
            return 0L;
        }
    }

    @Override
    public long getTransactionUpdateTime()
    {
        ServerTransaction serverTransaction = _transaction;
        if (serverTransaction.isTransactional())
        {
            return serverTransaction.getTransactionUpdateTime();
        }
        else
        {
            return 0L;
        }
    }

    private void consumerAdded(Consumer<?> consumer)
    {
        for(ConsumerListener l : _consumerListeners)
        {
            l.consumerAdded(consumer);
        }
    }

    private void consumerRemoved(Consumer<?> consumer)
    {
        for(ConsumerListener l : _consumerListeners)
        {
            l.consumerRemoved(consumer);
        }
    }

    @Override
    public boolean processPending()
    {
        if (!getAMQPConnection().isIOThread())
        {
            return false;
        }

        boolean desiredBlockingState = _blocking.get();
        if (desiredBlockingState != _wireBlockingState)
        {
            _wireBlockingState = desiredBlockingState;

            if (desiredBlockingState)
            {
                invokeBlock();
            }
            else
            {
                invokeUnblock();
            }
            _blockTime = desiredBlockingState ? System.currentTimeMillis() : 0;
        }

        boolean consumerListNeedsRefreshing;
        if(_consumersWithPendingWork.isEmpty())
        {
            _consumersWithPendingWork.addAll(getSubscriptions());
            consumerListNeedsRefreshing = false;
        }
        else
        {
            consumerListNeedsRefreshing = true;
        }

        Iterator<ConsumerTarget> iter = _consumersWithPendingWork.isEmpty() ? Collections.<ConsumerTarget>emptyIterator() : _consumersWithPendingWork.iterator();

        boolean consumerHasMoreWork = false;
        while(iter.hasNext())
        {
            final ConsumerTarget target = iter.next();
            iter.remove();
            if(target.hasPendingWork())
            {
                consumerHasMoreWork = true;
                target.processPending();
                break;
            }
        }

        return consumerHasMoreWork || consumerListNeedsRefreshing;
    }

    @Override
    public void addTicker(final Ticker ticker)
    {
        getConnection().getAmqpConnection().getAggregateTicker().addTicker(ticker);
        // trigger a wakeup to ensure the ticker will be taken into account
        getAMQPConnection().notifyWork();
    }

    @Override
    public void removeTicker(final Ticker ticker)
    {
        getConnection().getAmqpConnection().getAggregateTicker().removeTicker(ticker);
    }

    @Override
    public void notifyConsumerTargetCurrentStates()
    {
        Collection<ConsumerTarget_0_10> consumerTargets = getSubscriptions();
        for(ConsumerTarget_0_10 consumerTarget: consumerTargets)
        {
            consumerTarget.notifyCurrentState();
        }
    }

    @Override
    public void ensureConsumersNoticedStateChange()
    {
        Collection<ConsumerTarget_0_10> consumerTargets = getSubscriptions();
        for(ConsumerTarget_0_10 consumerTarget: consumerTargets)
        {
            try
            {
                consumerTarget.getSendLock();
            }
            finally
            {
                consumerTarget.releaseSendLock();
            }
        }
    }

    @Override
    public void doTimeoutAction(final String reason)
    {
        getAMQPConnection().closeSessionAsync(ServerSession.this, AMQConstant.RESOURCE_ERROR, reason);
    }

    public final long getMaxUncommittedInMemorySize()
    {
        return _maxUncommittedInMemorySize;
    }

    @Override
    public int compareTo(AMQSessionModel o)
    {
        return getId().compareTo(o.getId());
    }

    private class CheckCapacityAction implements Action<MessageInstance>
    {
        @Override
        public void performAction(final MessageInstance entry)
        {
            TransactionLogResource queue = entry.getOwningResource();
            if(queue instanceof CapacityChecker)
            {
                ((CapacityChecker)queue).checkCapacity(ServerSession.this);
            }
        }
    }

    private class ConsumerClosedListener implements ConfigurationChangeListener
    {
        @Override
        public void stateChanged(final ConfiguredObject object, final org.apache.qpid.server.model.State oldState, final org.apache.qpid.server.model.State newState)
        {
            if(newState == org.apache.qpid.server.model.State.DELETED)
            {
                consumerRemoved((Consumer<?>)object);
            }
        }

        @Override
        public void childAdded(final ConfiguredObject object, final ConfiguredObject child)
        {

        }

        @Override
        public void childRemoved(final ConfiguredObject object, final ConfiguredObject child)
        {

        }

        @Override
        public void attributeSet(final ConfiguredObject object,
                                 final String attributeName,
                                 final Object oldAttributeValue,
                                 final Object newAttributeValue)
        {

        }

        @Override
        public void bulkChangeStart(final ConfiguredObject<?> object)
        {

        }

        @Override
        public void bulkChangeEnd(final ConfiguredObject<?> object)
        {

        }
    }
}
