package org.apache.archiva.repository.mock;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.archiva.configuration.ManagedRepositoryConfiguration;
import org.apache.archiva.configuration.RemoteRepositoryConfiguration;
import org.apache.archiva.repository.BasicManagedRepository;
import org.apache.archiva.repository.BasicRemoteRepository;
import org.apache.archiva.repository.EditableManagedRepository;
import org.apache.archiva.repository.EditableRemoteRepository;
import org.apache.archiva.repository.ManagedRepository;
import org.apache.archiva.repository.PasswordCredentials;
import org.apache.archiva.repository.ReleaseScheme;
import org.apache.archiva.repository.RemoteRepository;
import org.apache.archiva.repository.RepositoryCredentials;
import org.apache.archiva.repository.RepositoryEvent;
import org.apache.archiva.repository.RepositoryException;
import org.apache.archiva.repository.RepositoryProvider;
import org.apache.archiva.repository.RepositoryType;
import org.apache.archiva.repository.features.ArtifactCleanupFeature;
import org.apache.archiva.repository.features.IndexCreationFeature;
import org.apache.archiva.repository.features.RemoteIndexFeature;
import org.apache.archiva.repository.features.StagingRepositoryFeature;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Period;
import java.util.HashSet;
import java.util.Set;

/**
 * Just a simple mock class for the repository provider
 */
@Service("mockRepositoryProvider")
public class RepositoryProviderMock implements RepositoryProvider
{

    private static final Set<RepositoryType> TYPES = new HashSet<>( );

    static
    {
        TYPES.add( RepositoryType.MAVEN );
        TYPES.add( RepositoryType.NPM );
    }

    @Override
    public Set<RepositoryType> provides( )
    {
        return TYPES;
    }

    @Override
    public EditableManagedRepository createManagedInstance( String id, String name )
    {
        return new BasicManagedRepository( id, name, Paths.get("target/repositories") );
    }

    @Override
    public EditableRemoteRepository createRemoteInstance( String id, String name )
    {
        return new BasicRemoteRepository( id, name , Paths.get("target/remotes"));
    }

    @Override
    public ManagedRepository createManagedInstance( ManagedRepositoryConfiguration configuration ) throws RepositoryException
    {
        BasicManagedRepository managedRepository = new BasicManagedRepository( configuration.getId( ), configuration.getName( ), Paths.get("target/repositories") );
        updateManagedInstance( managedRepository, configuration );
        return managedRepository;
    }


    @Override
    public void updateManagedInstance( EditableManagedRepository managedRepository, ManagedRepositoryConfiguration configuration ) throws RepositoryException
    {
        try
        {
            managedRepository.setName( managedRepository.getPrimaryLocale(), configuration.getName( ) );
            managedRepository.setLocation( new URI( configuration.getLocation( )==null ?"" : configuration.getLocation() ) );
            managedRepository.setBaseUri( new URI( "" ) );
            managedRepository.setBlocksRedeployment( configuration.isBlockRedeployments( ) );
            managedRepository.setDescription( managedRepository.getPrimaryLocale(), configuration.getDescription( ) );
            managedRepository.setLayout( configuration.getLayout( ) );
            managedRepository.setScanned( configuration.isScanned( ) );
            managedRepository.setSchedulingDefinition( configuration.getRefreshCronExpression( ) );
            if (configuration.isReleases()) {
                managedRepository.addActiveReleaseScheme( ReleaseScheme.RELEASE );
            }
            if (configuration.isSnapshots()) {
                managedRepository.addActiveReleaseScheme( ReleaseScheme.SNAPSHOT );
            }
            ArtifactCleanupFeature acf = managedRepository.getFeature( ArtifactCleanupFeature.class ).get( );
            acf.setRetentionPeriod( Period.ofDays( configuration.getRetentionPeriod( ) ) );
            acf.setDeleteReleasedSnapshots( configuration.isDeleteReleasedSnapshots( ) );
            acf.setRetentionCount( configuration.getRetentionCount( ) );
            IndexCreationFeature icf = managedRepository.getFeature( IndexCreationFeature.class ).get( );
            icf.setIndexPath( new URI( configuration.getIndexDir( ) ) );
            icf.setSkipPackedIndexCreation( configuration.isSkipPackedIndexCreation( ) );
            StagingRepositoryFeature srf = managedRepository.getFeature( StagingRepositoryFeature.class ).get( );
            srf.setStageRepoNeeded( configuration.isStageRepoNeeded( ) );
        }
        catch ( Exception e )
        {
            throw new RepositoryException( "Error", e );
        }

    }


    @Override
    public ManagedRepository createStagingInstance( ManagedRepositoryConfiguration configuration ) throws RepositoryException
    {
        String id = configuration.getId( ) + StagingRepositoryFeature.STAGING_REPO_POSTFIX;
        BasicManagedRepository managedRepository = new BasicManagedRepository( id, configuration.getName( ) , Paths.get("target/repositories"));
        updateManagedInstance( managedRepository, configuration );
        managedRepository.getFeature(StagingRepositoryFeature.class).get().setStageRepoNeeded(false);
        return managedRepository;
    }

    @Override
    public RemoteRepository createRemoteInstance( RemoteRepositoryConfiguration configuration ) throws RepositoryException
    {
        BasicRemoteRepository remoteRepository = new BasicRemoteRepository( configuration.getId( ), configuration.getName( ), Paths.get("target/remotes") );
        updateRemoteInstance( remoteRepository, configuration );
        return remoteRepository;
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public void updateRemoteInstance( EditableRemoteRepository remoteRepository, RemoteRepositoryConfiguration configuration ) throws RepositoryException
    {
        try
        {
            remoteRepository.setName( remoteRepository.getPrimaryLocale(), configuration.getName( ) );
            remoteRepository.setBaseUri( new URI( "" ) );
            remoteRepository.setDescription( remoteRepository.getPrimaryLocale(), configuration.getDescription( ) );
            remoteRepository.setLayout( configuration.getLayout( ) );
            remoteRepository.setSchedulingDefinition( configuration.getRefreshCronExpression( ) );
            remoteRepository.setCheckPath( configuration.getCheckPath( ) );
            remoteRepository.setExtraHeaders( configuration.getExtraHeaders( ) );
            remoteRepository.setExtraParameters( configuration.getExtraParameters( ) );
            remoteRepository.setTimeout( Duration.ofSeconds( configuration.getTimeout( ) ) );
            char[] pwd = configuration.getPassword()==null ? "".toCharArray() : configuration.getPassword().toCharArray();
            remoteRepository.setCredentials( new PasswordCredentials( configuration.getUsername( ), pwd ) );
            remoteRepository.setLocation( new URI( configuration.getUrl( )==null ? "" : configuration.getUrl() ) );
            RemoteIndexFeature rif = remoteRepository.getFeature( RemoteIndexFeature.class ).get( );
            rif.setDownloadRemoteIndexOnStartup( configuration.isDownloadRemoteIndexOnStartup( ) );
            rif.setDownloadRemoteIndex( configuration.isDownloadRemoteIndex( ) );
            rif.setIndexUri( new URI( configuration.getIndexDir( ) ) );
            rif.setDownloadTimeout( Duration.ofSeconds( configuration.getRemoteDownloadTimeout( ) ) );
            rif.setProxyId( configuration.getRemoteDownloadNetworkProxyId( ) );
        }
        catch ( Exception e )
        {
            throw new RepositoryException( "Error", e );
        }

    }

    @Override
    public ManagedRepositoryConfiguration getManagedConfiguration( ManagedRepository managedRepository ) throws RepositoryException
    {
        ManagedRepositoryConfiguration configuration = new ManagedRepositoryConfiguration( );
        configuration.setId( managedRepository.getId( ) );
        configuration.setName(managedRepository.getName());
        configuration.setLocation( managedRepository.getLocation( ) == null ? "" : managedRepository.getLocation().toString( ) );
        configuration.setBlockRedeployments( managedRepository.blocksRedeployments( ) );
        configuration.setDescription( managedRepository.getDescription( ) );
        configuration.setLayout( managedRepository.getLayout( ) );
        configuration.setScanned( managedRepository.isScanned( ) );
        configuration.setRefreshCronExpression( managedRepository.getSchedulingDefinition( ) );
        configuration.setReleases( managedRepository.getActiveReleaseSchemes().contains(ReleaseScheme.RELEASE) );
        configuration.setSnapshots( managedRepository.getActiveReleaseSchemes().contains(ReleaseScheme.SNAPSHOT) );
        ArtifactCleanupFeature acf = managedRepository.getFeature( ArtifactCleanupFeature.class ).get( );
        configuration.setRetentionPeriod( acf.getRetentionPeriod( ).getDays( ) );
        configuration.setDeleteReleasedSnapshots( acf.isDeleteReleasedSnapshots( ) );
        configuration.setRetentionCount( acf.getRetentionCount( ) );
        IndexCreationFeature icf = managedRepository.getFeature( IndexCreationFeature.class ).get( );
        configuration.setSkipPackedIndexCreation( icf.isSkipPackedIndexCreation( ) );
        configuration.setIndexDir( icf.getIndexPath( ) == null ? "" : icf.getIndexPath().toString( ) );
        StagingRepositoryFeature srf = managedRepository.getFeature( StagingRepositoryFeature.class ).get( );
        configuration.setStageRepoNeeded( srf.isStageRepoNeeded( ) );
        return configuration;
    }


    @Override
    public RemoteRepositoryConfiguration getRemoteConfiguration( RemoteRepository remoteRepository ) throws RepositoryException
    {
        RemoteRepositoryConfiguration configuration = new RemoteRepositoryConfiguration( );
        configuration.setId( remoteRepository.getId( ) );
        configuration.setName( remoteRepository.getName( ) );
        configuration.setDescription( remoteRepository.getDescription( ) );
        configuration.setLayout( remoteRepository.getLayout( ) );
        configuration.setRefreshCronExpression( remoteRepository.getSchedulingDefinition( ) );
        configuration.setCheckPath( remoteRepository.getCheckPath( ) );
        configuration.setExtraHeaders( remoteRepository.getExtraHeaders( ) );
        configuration.setExtraParameters( remoteRepository.getExtraParameters( ) );
        configuration.setTimeout( (int) remoteRepository.getTimeout( ).getSeconds( ) );
        RepositoryCredentials creds = remoteRepository.getLoginCredentials( );
        if (creds!=null)
        {
            PasswordCredentials pwdCreds = (PasswordCredentials) creds;
            configuration.setUsername( pwdCreds.getUsername( ) );
            configuration.setPassword( new String( pwdCreds.getPassword( ) ) );
        }
        configuration.setUrl( remoteRepository.getLocation( ) == null ? "" : remoteRepository.getLocation().toString( ) );
        RemoteIndexFeature rif = remoteRepository.getFeature( RemoteIndexFeature.class ).get( );
        configuration.setDownloadRemoteIndex( rif.isDownloadRemoteIndex( ) );
        configuration.setDownloadRemoteIndexOnStartup( rif.isDownloadRemoteIndexOnStartup( ) );
        configuration.setIndexDir( rif.getIndexUri( )==null ? "" : rif.getIndexUri().toString( ) );
        configuration.setRemoteDownloadNetworkProxyId( rif.getProxyId( ) );
        return configuration;
    }

    @Override
    public <T> void raise(RepositoryEvent<T> event) {

    }
}
