/*
 * Copyright 2017 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.profiler;

import com.navercorp.pinpoint.profiler.util.JavaAssistUtils;
import com.navercorp.pinpoint.profiler.util.LocationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.ClassFileTransformer;
import java.net.URL;
import java.security.ProtectionDomain;

/**
 * @author emeroad
 * @author jaehong.kim
 */
public class BaseClassFileTransformer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final boolean isDebug = logger.isDebugEnabled();

    private final ClassLoader agentClassLoader;

    public BaseClassFileTransformer(ClassLoader agentClassLoader) {
        this.agentClassLoader = agentClassLoader;
    }

    public byte[] transform(ClassLoader classLoader, String classInternalName, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classFileBuffer, ClassFileTransformer transformer) {
        final String className = JavaAssistUtils.jvmNameToJavaName(classInternalName);

        if (isDebug) {
            final URL url = LocationUtils.getLocation(protectionDomain);
            final String transform = getTransformState(classBeingRedefined);
            logger.debug("[{}] classLoader:{} className:{} transformer:{} url:{}",
                    transform, classLoader, className, transformer.getClass().getName(), url);
        }

        try {
            final Thread thread = Thread.currentThread();
            final ClassLoader before = getContextClassLoader(thread);
            thread.setContextClassLoader(this.agentClassLoader);
            try {
                return transformer.transform(classLoader, className, classBeingRedefined, protectionDomain, classFileBuffer);
            } finally {
                // The context class loader have to be recovered even if it was null.
                thread.setContextClassLoader(before);
            }
        } catch (Throwable e) {
            final URL location = LocationUtils.getLocation(protectionDomain);
            logger.error("Transformer:{} threw an exception. url:{} cl:{} ctxCl:{} agentCl:{} Cause:{}",
                    transformer.getClass().getName(), location, classLoader, Thread.currentThread().getContextClassLoader(), agentClassLoader, e.getMessage(), e);
            return null;
        }
    }

    private String getTransformState(Class<?> classBeingRedefined) {
        if (classBeingRedefined == null) {
            return "transform";
        }
        return "retransform";
    }

    private ClassLoader getContextClassLoader(Thread thread) throws Throwable {
        try {
            return thread.getContextClassLoader();
        } catch (SecurityException se) {
            throw se;
        } catch (Throwable th) {
            if (isDebug) {
                logger.debug("getContextClassLoader(). Caused:{}", th.getMessage(), th);
            }
            throw th;
        }
    }
}