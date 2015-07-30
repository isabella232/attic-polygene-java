/*
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
 */
package org.apache.zest.regression.qi65;

import org.junit.Test;
import org.apache.zest.api.composite.TransientComposite;
import org.apache.zest.api.mixin.Mixins;
import org.apache.zest.bootstrap.AssemblyException;
import org.apache.zest.bootstrap.ModuleAssembly;
import org.apache.zest.test.AbstractZestTest;

public class IssueTest
    extends AbstractZestTest
{
    private final static Class<?> CLAZZ = Object.class;
    private final static String METHOD_NAME = "toString";
    private final static Class<?> PARAM_TYPES[] = { };

    public void assemble( ModuleAssembly module )
        throws AssemblyException
    {
        module.transients( TestComposite.class );
    }

    @Test( expected = IllegalArgumentException.class )
    public void constraintOnMethodParameter()
        throws SecurityException, NoSuchMethodException
    {
        TestComposite test = module.newTransient( TestComposite.class );

        test.someMethod( null );
    }

    @Mixins( TestMixin.class )
    public interface TestComposite
        extends TransientComposite
    {
        String someMethod( String value );
    }

    public static abstract class TestMixin
        implements TestComposite
    {
        public String someMethod( String value )
        {
            return value + " " + value;
        }
    }
}