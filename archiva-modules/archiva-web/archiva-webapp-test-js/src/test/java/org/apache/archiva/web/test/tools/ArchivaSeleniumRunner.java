package org.apache.archiva.web.test.tools;
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

import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

import java.util.List;

/**
 * @author Olivier Lamy
 */
public class ArchivaSeleniumRunner
    extends BlockJUnit4ClassRunner
{

    /**
     * @param clazz Test class
     * @throws InitializationError if the test class is malformed.
     */
    public ArchivaSeleniumRunner( Class<?> clazz )
        throws InitializationError
    {
        super( clazz );
    }

    /*
     * FIXME move that to a Rule.
     */
    @Override
    protected Statement withAfters( FrameworkMethod method, Object target, Statement statement )
    {
        statement = super.withAfters( method, target, statement );
        return withAfterFailures( method, target, statement );
    }

    protected Statement withAfterFailures( FrameworkMethod method, Object target, Statement statement )
    {
        List<FrameworkMethod> failures = getTestClass().getAnnotatedMethods( AfterSeleniumFailure.class );
        return new RunAfterSeleniumFailures( statement, failures, target );
    }
}
