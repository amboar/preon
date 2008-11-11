/*
 * Copyright (C) 2008 Wilfred Springer
 * 
 * This file is part of Preon.
 * 
 * Preon is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2, or (at your option) any later version.
 * 
 * Preon is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * Preon; see the file COPYING. If not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Linking this library statically or dynamically with other modules is making a
 * combined work based on this library. Thus, the terms and conditions of the
 * GNU General Public License cover the whole combination.
 * 
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent modules, and
 * to copy and distribute the resulting executable under terms of your choice,
 * provided that you also meet, for each linked independent module, the terms
 * and conditions of the license of that module. An independent module is a
 * module which is not derived from or based on this library. If you modify this
 * library, you may extend this exception to your version of the library, but
 * you are not obligated to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 */

package nl.flotsam.preon.codec;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import nl.flotsam.limbo.Expression;
import nl.flotsam.preon.Builder;
import nl.flotsam.preon.Codec;
import nl.flotsam.preon.CodecDecorator;
import nl.flotsam.preon.CodecDescriptor;
import nl.flotsam.preon.CodecFactory;
import nl.flotsam.preon.DecodingException;
import nl.flotsam.preon.Resolver;
import nl.flotsam.preon.ResolverContext;
import nl.flotsam.preon.annotation.LazyLoading;
import nl.flotsam.preon.buffer.BitBuffer;

/**
 * An attempt to create a general purpose {@link CodecFactory} whose
 * {@link Codec Codecs} will only load their data once operations are invoked
 * upon them.
 * 
 * <p>
 * Note that there are some concurrency issues with this approach. The
 * {@link Codec} will create a proxy. When an operation is invoked on that
 * proxy, the proxy will check if it already obtained the actual value. This is
 * where it's getting nasty. Multiple threads might come in simultaneously.
 * </p>
 * 
 * @author Wilfred Springer
 * 
 */
public class LazyLoadingCodecDecorator implements CodecDecorator {

    public <T> Codec<T> decorate(Codec<T> decorated, AnnotatedElement metadata,
            Class<T> type, ResolverContext context) {
        if (metadata != null && metadata.isAnnotationPresent(LazyLoading.class)) {
            return new LazyLoadingCodec<T>(decorated, type);
        } else {
            return decorated;
        }
    }

    /**
     * A {@link Codec} that will only start loading the data when one of the
     * methods of that object are invoked.
     * 
     * @author Wilfred Springer (wis)
     * 
     * @param <T>
     */
    public static class LazyLoadingCodec<T> implements Codec<T> {

        /**
         * The {@link Codec} to use.
         */
        private Codec<T> wrapped;

        /**
         * The type of object created.
         */
        private Class<T> type;

        /**
         * Constructs a new instance.
         * 
         * @param wrapped
         *            The {@link Codec} to use when loading the data.
         * @param type
         *            The type of object that will be returned.
         */
        public LazyLoadingCodec(Codec<T> wrapped, Class<T> type) {
            this.wrapped = wrapped;
            this.type = type;
        }

        /*
         * (non-Javadoc)
         * @see nl.flotsam.preon.Codec#decode(nl.flotsam.preon.buffer.BitBuffer, nl.flotsam.preon.Resolver, nl.flotsam.preon.Builder)
         */
        @SuppressWarnings("unchecked")
        public T decode(final BitBuffer buffer, final Resolver resolver,
                final Builder builder) throws DecodingException {
            final int size = wrapped.getSize(resolver);
            final long pos = buffer.getBitPos();
            ClassLoader loader = this.getClass().getClassLoader();
            Enhancer enhancer = new Enhancer();
            enhancer.setClassLoader(loader);
            enhancer.setSuperclass(type);
            enhancer.setCallback(new MethodInterceptor() {

                private Object actual;

                public Object intercept(Object target, Method method,
                        Object[] args, MethodProxy proxy) throws Throwable {
                    if (actual == null) {
                        buffer.setBitPos(pos);
                        actual = wrapped.decode(buffer, resolver, builder);
                    }
                    return proxy.invoke(actual, args);
                }
            });
            buffer.setBitPos(pos + size);
            return (T) enhancer.create();
        }

        /*
         * (non-Javadoc)
         * @see nl.flotsam.preon.Codec#getCodecDescriptor()
         */
        public CodecDescriptor getCodecDescriptor() {
            return wrapped.getCodecDescriptor();
        }

        /*
         * (non-Javadoc)
         * @see nl.flotsam.preon.Codec#getSize(nl.flotsam.preon.Resolver)
         */
        public int getSize(Resolver resolver) {
            return wrapped.getSize(resolver);
        }

        /*
         * (non-Javadoc)
         * @see nl.flotsam.preon.Codec#getTypes()
         */
        public Class<?>[] getTypes() {
            return wrapped.getTypes();
        }

        /*
         * (non-Javadoc)
         * @see nl.flotsam.preon.Codec#getSize()
         */
        public Expression<Integer, Resolver> getSize() {
            return wrapped.getSize();
        }

        /*
         * (non-Javadoc)
         * @see nl.flotsam.preon.Codec#getType()
         */
        public Class<?> getType() {
            return type;
        }

    }

}
