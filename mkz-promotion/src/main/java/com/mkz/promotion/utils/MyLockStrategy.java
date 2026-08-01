package com.mkz.promotion.utils;

import com.mkz.common.exceptions.BizIllegalException;
import org.redisson.api.RLock;

public enum MyLockStrategy {
    SKIP_FAST(){
        @Override
        public boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            return lock.tryLock(0, prop.leaseTime(), prop.unit());
        }
        @Override
        public boolean tryLock(RLock lock, Idempotent prop) throws InterruptedException {
            return lock.tryLock(0, prop.leaseTime(), prop.unit());
        }
    },
    FAIL_FAST(){
        @Override
        public boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            boolean isLock = lock.tryLock(0, prop.leaseTime(), prop.unit());
            if (!isLock) {
                throw new BizIllegalException("请求太频繁");
            }
            return true;
        }
        @Override
        public boolean tryLock(RLock lock, Idempotent prop) throws InterruptedException {
            boolean isLock = lock.tryLock(0, prop.leaseTime(), prop.unit());
            if (!isLock) {
                throw new BizIllegalException("请求太频繁");
            }
            return true;
        }
    },
    KEEP_TRYING(){
        @Override
        public boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            lock.lock( prop.leaseTime(), prop.unit());
            return true;
        }
        @Override
        public boolean tryLock(RLock lock, Idempotent prop) throws InterruptedException {
            lock.lock( prop.leaseTime(), prop.unit());
            return true;
        }
    },
    SKIP_AFTER_RETRY_TIMEOUT(){
        @Override
        public boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            return lock.tryLock(prop.waitTime(), prop.leaseTime(), prop.unit());
        }
        @Override
        public boolean tryLock(RLock lock, Idempotent prop) throws InterruptedException {
            return lock.tryLock(prop.waitTime(), prop.leaseTime(), prop.unit());
        }
    },
    FAIL_AFTER_RETRY_TIMEOUT(){
        @Override
        public boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            boolean isLock = lock.tryLock(prop.waitTime(), prop.leaseTime(), prop.unit());
            if (!isLock) {
                throw new BizIllegalException("请求太频繁");
            }
            return true;
        }
        @Override
        public boolean tryLock(RLock lock, Idempotent prop) throws InterruptedException {
            boolean isLock = lock.tryLock(prop.waitTime(), prop.leaseTime(), prop.unit());
            if (!isLock) {
                throw new BizIllegalException("请求太频繁");
            }
            return true;
        }
    },
    ;

    public abstract boolean tryLock(RLock lock, MyLock prop) throws InterruptedException;

    /**
     * 重载方法：支持 Idempotent 注解，字段语义与 MyLock 一致
     */
    public abstract boolean tryLock(RLock lock, Idempotent prop) throws InterruptedException;
}