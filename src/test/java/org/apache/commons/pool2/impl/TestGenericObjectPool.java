/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.pool2.impl;

import java.lang.management.ManagementFactory;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

/**
 */
public class TestGenericObjectPool {

    public static class SimpleFactory implements PooledObjectFactory<String> {
        int makeCounter = 0;

        int activationCounter = 0;

        int validateCounter = 0;

        int activeCount = 0;

        boolean evenValid = true;

        boolean oddValid = true;

        boolean exceptionOnPassivate = false;

        boolean exceptionOnActivate = false;

        boolean exceptionOnDestroy = false;

        boolean enableValidation = true;

        long destroyLatency = 0;

        long makeLatency = 0;

        long validateLatency = 0;

        int maxTotal = Integer.MAX_VALUE;

        public SimpleFactory() {
            this(true);
        }

        public SimpleFactory(final boolean valid) {
            this(valid,valid);
        }
        public SimpleFactory(final boolean evalid, final boolean ovalid) {
            evenValid = evalid;
            oddValid = ovalid;
        }
        @Override
        public void activateObject(final PooledObject<String> obj) throws Exception {
            final boolean hurl;
            final boolean evenTest;
            final boolean oddTest;
            final int counter;
            synchronized(this) {
                hurl = exceptionOnActivate;
                evenTest = evenValid;
                oddTest = oddValid;
                counter = activationCounter++;
            }
            if (hurl) {
                if (!(counter%2 == 0 ? evenTest : oddTest)) {
                    throw new Exception();
                }
            }
        }
        @Override
        public void destroyObject(final PooledObject<String> obj) throws Exception {
            final long waitLatency;
            final boolean hurl;
            synchronized(this) {
                waitLatency = destroyLatency;
                hurl = exceptionOnDestroy;
            }
            if (waitLatency > 0) {
                doWait(waitLatency);
            }
            synchronized(this) {
                activeCount--;
            }
            if (hurl) {
                throw new Exception();
            }
        }
        private void doWait(final long latency) {
            try {
                Thread.sleep(latency);
            } catch (final InterruptedException ex) {
                // ignore
            }
        }
        public synchronized int getMakeCounter() {
            return makeCounter;
        }
        public synchronized boolean isThrowExceptionOnActivate() {
            return exceptionOnActivate;
        }
        public synchronized boolean isValidationEnabled() {
            return enableValidation;
        }
        @Override
        public PooledObject<String> makeObject() {
            final long waitLatency;
            synchronized(this) {
                activeCount++;
                if (activeCount > maxTotal) {
                    throw new IllegalStateException(
                        "Too many active instances: " + activeCount);
                }
                waitLatency = makeLatency;
            }
            if (waitLatency > 0) {
                doWait(waitLatency);
            }
            final int counter;
            synchronized(this) {
                counter = makeCounter++;
            }
            return new DefaultPooledObject<>(String.valueOf(counter));
        }
        @Override
        public void passivateObject(final PooledObject<String> obj) throws Exception {
            final boolean hurl;
            synchronized(this) {
                hurl = exceptionOnPassivate;
            }
            if (hurl) {
                throw new Exception();
            }
        }
        public synchronized void setDestroyLatency(final long destroyLatency) {
            this.destroyLatency = destroyLatency;
        }
        public synchronized void setEvenValid(final boolean valid) {
            evenValid = valid;
        }
        public synchronized void setMakeLatency(final long makeLatency) {
            this.makeLatency = makeLatency;
        }
        public synchronized void setMaxTotal(final int maxTotal) {
            this.maxTotal = maxTotal;
        }
        public synchronized void setOddValid(final boolean valid) {
            oddValid = valid;
        }

        public synchronized void setThrowExceptionOnActivate(final boolean b) {
            exceptionOnActivate = b;
        }

        public synchronized void setThrowExceptionOnDestroy(final boolean b) {
            exceptionOnDestroy = b;
        }

        public synchronized void setThrowExceptionOnPassivate(final boolean bool) {
            exceptionOnPassivate = bool;
        }

        public synchronized void setValid(final boolean valid) {
            setEvenValid(valid);
            setOddValid(valid);
        }

        public synchronized void setValidateLatency(final long validateLatency) {
            this.validateLatency = validateLatency;
        }

        public synchronized void setValidationEnabled(final boolean b) {
            enableValidation = b;
        }

        @Override
        public boolean validateObject(final PooledObject<String> obj) {
            final boolean validate;
            final boolean evenTest;
            final boolean oddTest;
            final long waitLatency;
            final int counter;
            synchronized(this) {
                validate = enableValidation;
                evenTest = evenValid;
                oddTest = oddValid;
                counter = validateCounter++;
                waitLatency = validateLatency;
            }
            if (waitLatency > 0) {
                doWait(waitLatency);
            }
            if (validate) {
                return counter%2 == 0 ? evenTest : oddTest;
            }
            return true;
        }
    }
    /*
     * Very simple test thread that just tries to borrow an object from
     * the provided pool returns it after a wait
     */
    static class WaitingTestThread extends Thread {
        private final GenericObjectPool<String> _pool;
        private final long _pause;
        private Throwable _thrown;

        private long preborrow; // just before borrow
        private long postborrow; //  borrow returned
        private long postreturn; // after object was returned
        private long ended;
        private String objectId;

        public WaitingTestThread(final GenericObjectPool<String> pool, final long pause) {
            _pool = pool;
            _pause = pause;
            _thrown = null;
        }

        @Override
        public void run() {
            try {
                preborrow = System.currentTimeMillis();
                final String obj = _pool.borrowObject();
                objectId = obj;
                postborrow = System.currentTimeMillis();
                Thread.sleep(_pause);
                System.out.println("enter returnObject");
                _pool.returnObject(obj);
                postreturn = System.currentTimeMillis();
            } catch (final Throwable e) {
                _thrown = e;
            } finally{
                ended = System.currentTimeMillis();
            }
        }
    }

    protected GenericObjectPool<String> genericObjectPool = null;

    private SimpleFactory simpleFactory = null;
    @Before
    public void setUp() throws Exception {
        simpleFactory = new SimpleFactory();
        genericObjectPool = new GenericObjectPool<>(simpleFactory);
    }

    @After
    public void tearDown() throws Exception {
        final String poolName = genericObjectPool.getJmxName().toString();
        genericObjectPool.clear();
        genericObjectPool.close();
        genericObjectPool = null;
        simpleFactory = null;

        final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        final Set<ObjectName> result = mbs.queryNames(new ObjectName(
                "org.apache.commoms.pool2:type=GenericObjectPool,*"), null);
        // There should be no registered pools at this point
        final int registeredPoolCount = result.size();
        final StringBuilder msg = new StringBuilder("Current pool is: ");
        msg.append(poolName);
        msg.append("  Still open pools are: ");
        for (final ObjectName name : result) {
            // Clean these up ready for the next test
            msg.append(name.toString());
            msg.append(" created via\n");
            msg.append(mbs.getAttribute(name, "CreationStackTrace"));
            msg.append('\n');
            mbs.unregisterMBean(name);
        }
        Assert.assertEquals(msg.toString(), 0, registeredPoolCount);
    }
    /**
     * POOL-376
     * @throws Exception 
     */
    @Test()
    public void testNoInvalidateNPE() throws Exception{
         
        genericObjectPool.setMaxTotal(1);//set cap object's num  can be allocated
        genericObjectPool.setTestOnCreate(true);  //validated before  being returned from borrowObject
        genericObjectPool.setMaxWaitMillis(-1);
        String obj = genericObjectPool.borrowObject();
        // Make validation fail - this will cause create() to return null
        simpleFactory.setValid(false);
       //  Create a take waiter
        final WaitingTestThread wtt = new WaitingTestThread(genericObjectPool, 200);
        wtt.start();
        // Give wtt time to start
        Thread.sleep(200);
        genericObjectPool.invalidateObject(obj);
        // Now allow create to succeed so waiter can be served
        simpleFactory.setValid(true);
    }
    
    public static void main(String[] args) {
        Result result = JUnitCore.runClasses(TestGenericObjectPool.class);
        if(result.wasSuccessful()) {
            System.out.println("test sucess");
        }else {
            System.out.println("test failed");
            System.out.println(result.getFailures().get(0).getTrace());
        }
     
        System.exit(result.wasSuccessful() ? 0 : 1);
    }
}
