/*
 * Copyright (c) 2010, Rickard Öberg. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.zest.regression.qi230;

import org.junit.Test;
import org.apache.zest.api.ZestAPI;
import org.apache.zest.api.composite.Composite;
import org.apache.zest.api.concern.ConcernOf;
import org.apache.zest.api.concern.Concerns;
import org.apache.zest.api.injection.scope.Service;
import org.apache.zest.api.injection.scope.Structure;
import org.apache.zest.api.injection.scope.This;
import org.apache.zest.api.mixin.Mixins;
import org.apache.zest.api.mixin.NoopMixin;
import org.apache.zest.api.service.ServiceComposite;
import org.apache.zest.bootstrap.AssemblyException;
import org.apache.zest.bootstrap.ModuleAssembly;
import org.apache.zest.test.AbstractZestTest;

import static org.junit.Assert.assertEquals;

/**
 * JAVADOC
 */
public class Qi230IssueTest
    extends AbstractZestTest
{
    public void assemble( ModuleAssembly module )
        throws AssemblyException
    {
        module.services( Some.class ).withMixins( NoopMixin.class ).withConcerns( OtherConcern.class );
//        module.services( Some.class );
        module.services( Result.class );
    }

    @Test
    public void whenDerefencingInsideConcernThisExpectItToWork()
        throws Exception
    {
        Result result = serviceFinder.findService( Result.class ).get();
        Some some = serviceFinder.findService( Some.class ).get();
        assertEquals( "method()", some.method() );
        assertEquals( some.identity(), result.some().identity() );
        assertEquals( some.identity().get(), result.some().identity().get() );
    }

    @Mixins( ResultMixin.class )
    public interface Result
        extends ServiceComposite
    {
        void execute( Some value );

        Some some();
    }

    public static abstract class ResultMixin
        implements Result
    {

        private Some value;

        public void execute( Some value )
        {
            this.value = value;
        }

        public Some some()
        {
            return value;
        }
    }

    @Concerns( OtherConcern.class )
    @Mixins( NoopMixin.class )
    public interface Other
    {
        void other();
    }

    @Mixins( SomeMixin.class )
    public interface Some
        extends ServiceComposite
//        extends ServiceComposite, Other
    {
        String method();
    }

    public abstract static class SomeMixin
        implements Some
    {
        @This
        private Other other;

        public String method()
        {
            other.other();
            return "method()";
        }
    }

    public static class OtherConcern
        extends ConcernOf<Other>
        implements Other
    {
        @Structure
        private ZestAPI api;

        @This
        private Composite me;

        @Service
        private Result result;

        public void other()
        {
            Composite value = api.dereference( me );
            result.execute( (Some) value );
            next.other();
        }
    }
}
