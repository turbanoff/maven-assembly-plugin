package org.apache.maven.plugins.assembly.archive.archiver;

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

import org.apache.maven.plugins.assembly.testutils.TestFileManager;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.diags.TrackingArchiver;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.util.DefaultFileSet;
import org.codehaus.plexus.components.io.fileselectors.FileInfo;
import org.codehaus.plexus.components.io.fileselectors.FileSelector;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.codehaus.plexus.util.FileUtils;
import org.easymock.EasyMock;
import org.easymock.classextension.EasyMockSupport;
import org.junit.AfterClass;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.easymock.EasyMock.anyObject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AssemblyProxyArchiverTest
{

    private static final TestFileManager fileManager = new TestFileManager( "massembly-proxyArchiver", "" );

    private static final Logger logger = new ConsoleLogger( Logger.LEVEL_DEBUG, "test" );

    @AfterClass
    public static void cleanupFiles()
    {
        fileManager.cleanUp();
    }

    @Test( timeout = 5000 )
    public void addFileSet_SkipWhenSourceIsAssemblyWorkDir()
        throws IOException, ArchiverException
    {
        final File sources = fileManager.createTempDir();

        final File workdir = new File( sources, "workdir" );

        final TrackingArchiver tracker = new TrackingArchiver();
        final AssemblyProxyArchiver archiver =
            new AssemblyProxyArchiver( "", tracker, null, null, null, workdir, logger );

        archiver.setForced( true );

        final DefaultFileSet fs = new DefaultFileSet();
        fs.setDirectory( workdir );

        archiver.addFileSet( fs );

        assertTrue( tracker.added.isEmpty() );
    }

    @Test( timeout = 5000 )
    public void addFileSet_addExcludeWhenSourceContainsAssemblyWorkDir()
        throws IOException, ArchiverException
    {
        final File sources = fileManager.createTempDir();

        final File workdir = new File( sources, "workdir" );
        workdir.mkdir();

        fileManager.createFile( sources, "test-included.txt", "This is included" );
        fileManager.createFile( workdir, "test-excluded.txt", "This is excluded" );

        final TrackingArchiver tracker = new TrackingArchiver();
        final AssemblyProxyArchiver archiver =
            new AssemblyProxyArchiver( "", tracker, null, null, null, workdir, logger );

        archiver.setForced( true );

        final DefaultFileSet fs = new DefaultFileSet();
        fs.setDirectory( sources );

        archiver.addFileSet( fs );

        assertEquals( 1, tracker.added.size() );

        final TrackingArchiver.Addition addition = tracker.added.get( 0 );
        assertNotNull( addition.excludes );
        assertEquals( 1, addition.excludes.length );
        assertEquals( workdir.getName(), addition.excludes[0] );
    }

    @Test
    public void addFile_NoPerms_CallAcceptFilesOnlyOnce()
        throws IOException, ArchiverException
    {
        EasyMockSupport mm = new EasyMockSupport();
        final Archiver delegate = mm.createMock( Archiver.class );

        delegate.addFile( (File) anyObject(), (String) anyObject() );
        EasyMock.expectLastCall().anyTimes();

        delegate.setForced( true );
        EasyMock.expectLastCall().anyTimes();

        final CounterSelector counter = new CounterSelector( true );
        final List<FileSelector> selectors = new ArrayList<>();
        selectors.add( counter );

        mm.replayAll();

        final AssemblyProxyArchiver archiver =
            new AssemblyProxyArchiver( "", delegate, null, selectors, null, new File( "." ),
                                       new ConsoleLogger( Logger.LEVEL_DEBUG, "test" ) );

        archiver.setForced( true );

        final File inputFile = fileManager.createTempFile();

        archiver.addFile( inputFile, "file.txt" );

        assertEquals( 1, counter.getCount() );

        mm.verifyAll();
    }

    @Test
    public void addDirectory_NoPerms_CallAcceptFilesOnlyOnce()
        throws IOException, ArchiverException
    {
        final Archiver delegate = new JarArchiver();

        final File output = fileManager.createTempFile();

        delegate.setDestFile( output );

        final CounterSelector counter = new CounterSelector( true );
        final List<FileSelector> selectors = new ArrayList<>();
        selectors.add( counter );

        final AssemblyProxyArchiver archiver =
            new AssemblyProxyArchiver( "", delegate, null, selectors, null, new File( "." ),
                                       new ConsoleLogger( Logger.LEVEL_DEBUG, "test" ) );

        archiver.setForced( true );

        final File dir = fileManager.createTempDir();
        FileUtils.cleanDirectory( dir );
        fileManager.createFile( dir, "file.txt", "This is a test." );

        archiver.addDirectory( dir );

        archiver.createArchive();

        assertEquals( 1, counter.getCount() );
    }

    private static final class CounterSelector
        implements FileSelector
    {

        private int count = 0;

        private boolean answer = false;

        public CounterSelector( final boolean answer )
        {
            this.answer = answer;
        }

        public int getCount()
        {
            return count;
        }

        public boolean isSelected( final @Nonnull FileInfo fileInfo )
            throws IOException
        {
            if ( fileInfo.isFile() )
            {
                count++;
                System.out.println( "Counting file: " + fileInfo.getName() + ". Current count: " + count );
            }

            return answer;
        }

    }

}
