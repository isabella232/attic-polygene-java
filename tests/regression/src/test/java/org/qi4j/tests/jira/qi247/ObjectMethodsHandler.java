/*
 * Copyright (c) 2010, Lukasz Zielinski. All Rights Reserved.
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
package org.qi4j.tests.jira.qi247;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class ObjectMethodsHandler
    implements InvocationHandler
{
    public Object invoke( Object proxy, Method method, Object[] args )
        throws Throwable
    {
        System.out.println( "invoke(proxy, " + method.getName() + ", args" );
        if( "toString".equals( method.getName() ) )
        {
            return ObjectMethods.MESSAGE;
        }
        else if( "hashCode".equals( method.getName() ) )
        {
            return ObjectMethods.CODE;
        }
        else if( "equals".equals( method.getName() ) )
        {
            return proxy == args[ 0 ];
        }
        else
        {
            throw new UnsupportedOperationException( method.toString() );
        }
    }
}