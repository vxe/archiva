package org.apache.archiva.web.xmlrpc.services;

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
 

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.archiva.indexer.search.RepositorySearch;
import org.apache.archiva.indexer.search.SearchResultHit;
import org.apache.archiva.indexer.search.SearchResultLimits;
import org.apache.archiva.indexer.search.SearchResults;
import org.apache.archiva.indexer.util.SearchUtil;
import org.apache.archiva.web.xmlrpc.api.SearchService;
import org.apache.archiva.web.xmlrpc.api.beans.Artifact;
import org.apache.archiva.web.xmlrpc.api.beans.Dependency;
import org.apache.archiva.web.xmlrpc.security.XmlRpcUserRepositories;
import org.apache.maven.archiva.database.ArchivaDAO;
import org.apache.maven.archiva.database.ArchivaDatabaseException;
import org.apache.maven.archiva.database.ArtifactDAO;
import org.apache.maven.archiva.database.ObjectNotFoundException;
import org.apache.maven.archiva.database.browsing.BrowsingResults;
import org.apache.maven.archiva.database.browsing.RepositoryBrowsing;
import org.apache.maven.archiva.database.constraints.ArtifactsByChecksumConstraint;
import org.apache.maven.archiva.database.constraints.UniqueVersionConstraint;
import org.apache.maven.archiva.model.ArchivaArtifact;
import org.apache.maven.archiva.model.ArchivaProjectModel;
import org.codehaus.plexus.spring.PlexusInSpringTestCase;
import org.easymock.MockControl;
import org.easymock.classextension.MockClassControl;
import org.slf4j.Logger;

/**
 * SearchServiceImplTest
 * 
 * @version $Id: SearchServiceImplTest.java
 */
public class SearchServiceImplTest
    extends PlexusInSpringTestCase
{
    private SearchService searchService;
    
    private MockControl userReposControl;
    
    private XmlRpcUserRepositories userRepos;
    
    private MockControl searchControl;
    
    private RepositorySearch search;
        
    private MockControl archivaDAOControl;
    
    private ArchivaDAO archivaDAO;
    
    private MockControl artifactDAOControl;
    
    private ArtifactDAO artifactDAO;
    
    private MockControl repoBrowsingControl;
    
    private RepositoryBrowsing repoBrowsing;

    private MockControl loggerControl;

    private Logger logger;

    private static final String ARCHIVA_TEST_ARTIFACT_ID = "archiva-xmlrpc-test";

    private static final String ARCHIVA_TEST_GROUP_ID = "org.apache.archiva";

    @Override
    public void setUp()
        throws Exception
    {
        userReposControl = MockClassControl.createControl( XmlRpcUserRepositories.class );
        userRepos = ( XmlRpcUserRepositories ) userReposControl.getMock();
                
        archivaDAOControl = MockControl.createControl( ArchivaDAO.class );
        archivaDAOControl.setDefaultMatcher( MockControl.ALWAYS_MATCHER );
        archivaDAO = ( ArchivaDAO ) archivaDAOControl.getMock();
        
        repoBrowsingControl = MockControl.createControl( RepositoryBrowsing.class );
        repoBrowsing = ( RepositoryBrowsing ) repoBrowsingControl.getMock();
        
        searchControl = MockControl.createControl( RepositorySearch.class );
        searchControl.setDefaultMatcher( MockControl.ALWAYS_MATCHER );
        search = ( RepositorySearch ) searchControl.getMock();
        
        searchService = new SearchServiceImpl( userRepos, archivaDAO, repoBrowsing, search );
        
        artifactDAOControl = MockControl.createControl( ArtifactDAO.class );        
        artifactDAO = ( ArtifactDAO ) artifactDAOControl.getMock();

        loggerControl = MockControl.createControl( Logger.class );
        logger = ( Logger ) loggerControl.getMock();

    }
    
    // MRM-1230
    public void testQuickSearchModelPackagingIsUsed()
        throws Exception
    {  
        List<String> observableRepoIds = new ArrayList<String>();
        observableRepoIds.add( "repo1.mirror" );
        observableRepoIds.add( "public.releases" );
        
        userReposControl.expectAndReturn( userRepos.getObservableRepositories(), observableRepoIds );
        
        SearchResults results = new SearchResults();         
        List<String> versions = new ArrayList<String>();
        versions.add( "1.0" );
        
        SearchResultHit resultHit = new SearchResultHit();
        resultHit.setGroupId( ARCHIVA_TEST_GROUP_ID );
        resultHit.setArtifactId( "archiva-webapp" );        
        resultHit.setVersions( versions );
        resultHit.setRepositoryId( null );
        
        results.addHit( SearchUtil.getHitId( ARCHIVA_TEST_GROUP_ID, "archiva-webapp" ), resultHit );
        
        SearchResultLimits limits = new SearchResultLimits( SearchResultLimits.ALL_PAGES );
        
        searchControl.expectAndDefaultReturn( search.search( "", observableRepoIds, "archiva", limits, null ), results );
        
        archivaDAOControl.expectAndReturn( archivaDAO.query( new UniqueVersionConstraint( observableRepoIds, resultHit.getGroupId(),
                                                                                          resultHit.getArtifactId() ) ), null );
        
        ArchivaProjectModel model = new ArchivaProjectModel();
        model.setGroupId( ARCHIVA_TEST_GROUP_ID );
        model.setArtifactId( "archiva-webapp" );
        model.setVersion( "1.0" );
        model.setPackaging( "war" );
          
        repoBrowsingControl.expectAndReturn( repoBrowsing.selectVersion( "", observableRepoIds, ARCHIVA_TEST_GROUP_ID, "archiva-webapp", "1.0" ), model );
                
        repoBrowsingControl.expectAndReturn( repoBrowsing.getRepositoryId( "", observableRepoIds, ARCHIVA_TEST_GROUP_ID, "archiva-webapp", "1.0" ), "repo1.mirror" );
        
        userReposControl.replay();
        searchControl.replay();
        repoBrowsingControl.replay();
        archivaDAOControl.replay();
          
        List<Artifact> artifacts = searchService.quickSearch( "archiva" );
        
        userReposControl.verify();
        searchControl.verify();
        repoBrowsingControl.verify();
        archivaDAOControl.verify();
        
        assertNotNull( artifacts );
        assertEquals( 1, artifacts.size() );
          
        Artifact artifact = artifacts.get( 0 );
        assertEquals( ARCHIVA_TEST_GROUP_ID, artifact.getGroupId() );
        assertEquals( "archiva-webapp", artifact.getArtifactId() );
        assertEquals( "1.0", artifact.getVersion() );
        assertEquals( "war", artifact.getType() );
        assertNotNull( "Repository should not be null!", artifact.getRepositoryId() );
        assertEquals( "repo1.mirror", artifact.getRepositoryId() );
    }
    
    // returned model is null!
    public void testQuickSearchDefaultPackagingIsUsed()
        throws Exception
    {
        List<String> observableRepoIds = new ArrayList<String>();
        observableRepoIds.add( "repo1.mirror" );
        observableRepoIds.add( "public.releases" );
        
        userReposControl.expectAndReturn( userRepos.getObservableRepositories(), observableRepoIds );
        
        SearchResults results = new SearchResults();        
        List<String> versions = new ArrayList<String>();
        versions.add( "1.0" );
        
        SearchResultHit resultHit = new SearchResultHit();
        resultHit.setRepositoryId( null );
        resultHit.setGroupId( ARCHIVA_TEST_GROUP_ID );
        resultHit.setArtifactId( ARCHIVA_TEST_ARTIFACT_ID );
        resultHit.setVersions( versions );
        
        results.addHit( SearchUtil.getHitId( ARCHIVA_TEST_GROUP_ID, ARCHIVA_TEST_ARTIFACT_ID ), resultHit );
        
        SearchResultLimits limits = new SearchResultLimits( SearchResultLimits.ALL_PAGES );
        
        searchControl.expectAndDefaultReturn( search.search( "", observableRepoIds, "archiva", limits, null ), results );
        
        archivaDAOControl.expectAndReturn( archivaDAO.query( new UniqueVersionConstraint( observableRepoIds, resultHit.getGroupId(),
                                                                                          resultHit.getArtifactId() ) ), null );
          
        repoBrowsingControl.expectAndReturn( repoBrowsing.selectVersion( "", observableRepoIds, ARCHIVA_TEST_GROUP_ID,
                                                                         ARCHIVA_TEST_ARTIFACT_ID, "1.0" ), null );
        
        repoBrowsingControl.expectAndReturn( repoBrowsing.getRepositoryId( "", observableRepoIds, ARCHIVA_TEST_GROUP_ID,
                                                                           ARCHIVA_TEST_ARTIFACT_ID, "1.0" ), null );
        
        userReposControl.replay();
        searchControl.replay();
        repoBrowsingControl.replay();
        archivaDAOControl.replay();  
        
        List<Artifact> artifacts = searchService.quickSearch( "archiva" );
        
        userReposControl.verify();
        searchControl.verify();
        repoBrowsingControl.verify();
        archivaDAOControl.verify();
        
        assertNotNull( artifacts );
        assertEquals( 1, artifacts.size() );
          
        Artifact artifact = artifacts.get( 0 );
        assertEquals( ARCHIVA_TEST_GROUP_ID, artifact.getGroupId() );
        assertEquals( ARCHIVA_TEST_ARTIFACT_ID, artifact.getArtifactId() );
        assertEquals( "1.0", artifact.getVersion() );
        assertEquals( "jar", artifact.getType() );
        assertNull( "Repository should be null since the model was not found in the database!", artifact.getRepositoryId() );
    }

    public void testQuickSearchArtifactObjectNotFoundException()
    	throws Exception
    {
    	List<String> observableRepoIds = new ArrayList<String>();
        observableRepoIds.add( "repo1.mirror" );

        userReposControl.expectAndReturn( userRepos.getObservableRepositories(), observableRepoIds );
        
        SearchResults results = new SearchResults();        
        List<String> versions = new ArrayList<String>();
        versions.add( "1.0" );
        
        SearchResultHit resultHit = new SearchResultHit();
        resultHit.setRepositoryId( "repo1.mirror" );
        resultHit.setGroupId( "org.apache.archiva" );
        resultHit.setArtifactId( "archiva-test" );
        resultHit.setVersions( versions );

        results.addHit( SearchUtil.getHitId( "org.apache.archiva", "archiva-test" ), resultHit );
        
        SearchResultLimits limits = new SearchResultLimits( SearchResultLimits.ALL_PAGES );
        
        searchControl.expectAndDefaultReturn( search.search( "", observableRepoIds, "archiva", limits, null ), results );

        archivaDAOControl.expectAndReturn( archivaDAO.query( new UniqueVersionConstraint( observableRepoIds, resultHit.getGroupId(),
                                                                                          resultHit.getArtifactId() ) ), null );

        ObjectNotFoundException exception = new ObjectNotFoundException( "org.apache.archiva.archiva-test:1.0" );

        repoBrowsingControl.expectAndThrow( repoBrowsing.selectVersion( "", observableRepoIds, "org.apache.archiva", "archiva-test", "1.0" ), exception );

        logger.debug( "Unable to find pom artifact : " + exception.getMessage() );
        loggerControl.setDefaultVoidCallable();

        userReposControl.replay();
        searchControl.replay();
        repoBrowsingControl.replay();
        archivaDAOControl.replay();
        loggerControl.replay();
        
        List<Artifact> artifacts = searchService.quickSearch( "archiva" );
        
        userReposControl.verify();
        searchControl.verify();
        repoBrowsingControl.verify();
        archivaDAOControl.verify();
	    loggerControl.verify();
        
        assertNotNull( artifacts );
        assertEquals( 0, artifacts.size() );
    }

    public void testQuickSearchArtifactArchivaDatabaseException()
		throws Exception
    {
    	List<String> observableRepoIds = new ArrayList<String>();
        observableRepoIds.add( "repo1.mirror" );

        userReposControl.expectAndReturn( userRepos.getObservableRepositories(), observableRepoIds );
        
        SearchResults results = new SearchResults();        
        List<String> versions = new ArrayList<String>();
        versions.add( "1.0" );
        
        SearchResultHit resultHit = new SearchResultHit();
        resultHit.setRepositoryId( "repo1.mirror" );
        resultHit.setGroupId( "org.apache.archiva" );
        resultHit.setArtifactId( "archiva-test" );
        resultHit.setVersions( versions );

        results.addHit( SearchUtil.getHitId( "org.apache.archiva", "archiva-test" ), resultHit );
        
        SearchResultLimits limits = new SearchResultLimits( SearchResultLimits.ALL_PAGES );
        
        searchControl.expectAndDefaultReturn( search.search( "", observableRepoIds, "archiva", limits, null ), results );

        archivaDAOControl.expectAndReturn( archivaDAO.query( new UniqueVersionConstraint( observableRepoIds, resultHit.getGroupId(),
                                                                                          resultHit.getArtifactId() ) ), null );

        ArchivaDatabaseException exception = new ArchivaDatabaseException( "org.apache.archiva.archiva-test:1.0" );

        repoBrowsingControl.expectAndThrow( repoBrowsing.selectVersion( "", observableRepoIds, "org.apache.archiva", "archiva-test", "1.0" ), exception );

        logger.debug( "Error occurred while getting pom artifact from database : " + exception.getMessage() );
        loggerControl.setDefaultVoidCallable();

        userReposControl.replay();
        searchControl.replay();
        repoBrowsingControl.replay();
        archivaDAOControl.replay();
        loggerControl.replay();
        
        List<Artifact> artifacts = searchService.quickSearch( "archiva" );
        
        userReposControl.verify();
        searchControl.verify();
        repoBrowsingControl.verify();
        archivaDAOControl.verify();
        loggerControl.verify();
        
        assertNotNull( artifacts );
        assertEquals( 0, artifacts.size() );
    }
    
    /*
     * quick/general text search which returns a list of artifacts
     * query for an artifact based on a checksum
     * query for all available versions of an artifact, sorted in version significance order
     * query for all available versions of an artifact since a given date
     * query for an artifact's direct dependencies
     * query for an artifact's dependency tree (as with mvn dependency:tree - no duplicates should be included)
     * query for all artifacts that depend on a given artifact
     */
 
 /* quick search */
    
//    public void testQuickSearchArtifactBytecodeSearch()
//        throws Exception
//    {
//        // 1. check whether bytecode search or ordinary search
//        // 2. get observable repos
//        // 3. convert results to a list of Artifact objects
//
//        List<String> observableRepoIds = new ArrayList<String>();
//        observableRepoIds.add( "repo1.mirror" );
//        observableRepoIds.add( "public.releases" );
//
//        userReposControl.expectAndReturn( userRepos.getObservableRepositories(), observableRepoIds );
//
//        Date whenGathered = new Date();
//        SearchResults results = new SearchResults();
//        ArchivaArtifact artifact = new ArchivaArtifact( "org.apache.archiva", "archiva-test", "1.0", "", "jar", "public.releases" );
//        artifact.getModel().setWhenGathered( whenGathered );
//
//        SearchResultHit resultHit = new SearchResultHit();
//        resultHit.setArtifact(artifact);
//        resultHit.setRepositoryId("repo1.mirror");
//
//        results.addHit(SearchUtil.getHitId(artifact.getGroupId(), artifact.getArtifactId()), resultHit);
//
//        SearchResultLimits limits = new SearchResultLimits( SearchResultLimits.ALL_PAGES );
//
//        searchControl.expectAndDefaultReturn( search.search( "", observableRepoIds, "MyClassName", limits, null ), results );
//
//        archivaDAOControl.expectAndReturn( archivaDAO.getArtifactDAO(), artifactDAO );
//        artifactDAOControl.expectAndReturn( artifactDAO.getArtifact( "org.apache.archiva", "archiva-test", "1.0", "", "pom", "public.releases" ), artifact );
//
//        userReposControl.replay();
//        searchControl.replay();
//        archivaDAOControl.replay();
//        artifactDAOControl.replay();
//
//        List<Artifact> artifacts = searchService.quickSearch( "bytecode:MyClassName" );
//
//        userReposControl.verify();
//        searchControl.verify();
//        archivaDAOControl.verify();
//        artifactDAOControl.verify();
//
//        assertNotNull( artifacts );
//        assertEquals( 1, artifacts.size() );
//    }
    
    public void testQuickSearchArtifactRegularSearch()
        throws Exception
    {
        List<String> observableRepoIds = new ArrayList<String>();
        observableRepoIds.add( "repo1.mirror" );
        observableRepoIds.add( "public.releases" );
    
        userReposControl.expectAndReturn( userRepos.getObservableRepositories(), observableRepoIds );
       
        SearchResults results = new SearchResults();       
        List<String> versions = new ArrayList<String>();
        versions.add( "1.0" );
        
        SearchResultHit resultHit = new SearchResultHit();
        resultHit.setGroupId( ARCHIVA_TEST_GROUP_ID );
        resultHit.setArtifactId( ARCHIVA_TEST_ARTIFACT_ID );
        resultHit.setVersions( versions );
        resultHit.setRepositoryId( null );
        
        results.addHit( SearchUtil.getHitId( resultHit.getGroupId(), resultHit.getArtifactId() ), resultHit );
    
        archivaDAOControl.expectAndReturn( archivaDAO.query( new UniqueVersionConstraint( observableRepoIds, resultHit.getGroupId(),
                                                                                          resultHit.getArtifactId() ) ), null );
        
        SearchResultLimits limits = new SearchResultLimits( SearchResultLimits.ALL_PAGES );
    
        searchControl.expectAndDefaultReturn( search.search( "", observableRepoIds, "archiva", limits, null ), results );
        
        ArchivaProjectModel model = new ArchivaProjectModel();
        model.setGroupId( ARCHIVA_TEST_GROUP_ID );
        model.setArtifactId( ARCHIVA_TEST_ARTIFACT_ID );
        model.setVersion( "1.0" );
        model.setPackaging( "jar" );
          
        repoBrowsingControl.expectAndReturn( repoBrowsing.selectVersion( "", observableRepoIds, ARCHIVA_TEST_GROUP_ID,
                                                                         ARCHIVA_TEST_ARTIFACT_ID, "1.0" ), model );
        
        repoBrowsingControl.expectAndReturn( repoBrowsing.getRepositoryId( "", observableRepoIds, ARCHIVA_TEST_GROUP_ID,
                                                                           ARCHIVA_TEST_ARTIFACT_ID, "1.0" ), "repo1.mirror" );
        
        userReposControl.replay();
        searchControl.replay();
        archivaDAOControl.replay();
        repoBrowsingControl.replay();
    
        List<Artifact> artifacts = searchService.quickSearch( "archiva" );
        
        userReposControl.verify();
        searchControl.verify();
        archivaDAOControl.verify();
        repoBrowsingControl.verify();
      
        assertNotNull( artifacts );
        assertEquals( 1, artifacts.size() );
        
        Artifact artifact = artifacts.get( 0 );
        assertEquals( ARCHIVA_TEST_GROUP_ID, artifact.getGroupId() );
        assertEquals( ARCHIVA_TEST_ARTIFACT_ID, artifact.getArtifactId() );
        assertEquals( "1.0", artifact.getVersion() );
        assertEquals( "jar", artifact.getType() );
        assertNotNull( "Repository should not be null!", artifact.getRepositoryId() );
        assertEquals( "repo1.mirror", artifact.getRepositoryId() );
    }
    
    public void testQuickSearchNoResults( )
        throws Exception
    {
        List<String> observableRepoIds = new ArrayList<String>();
        observableRepoIds.add( "repo1.mirror" );
        observableRepoIds.add( "public.releases" );
    
        userReposControl.expectAndReturn( userRepos.getObservableRepositories(), observableRepoIds );
    
        SearchResults results = new SearchResults();
        SearchResultLimits limits = new SearchResultLimits( SearchResultLimits.ALL_PAGES );
    
        searchControl.expectAndDefaultReturn( search.search( "", observableRepoIds, "non-existent", limits, null ), results );
        userReposControl.replay();
        searchControl.replay();
    
        List<Artifact> artifacts = searchService.quickSearch( "test" );
    
        userReposControl.verify();
        searchControl.verify();
    
        assertNotNull( artifacts );
        assertEquals( 0, artifacts.size() );
    }     
    
/* query artifact by checksum */
    
    public void testGetArtifactByChecksum()
        throws Exception
    {
        Date whenGathered = new Date();
        
        ArtifactsByChecksumConstraint constraint = new ArtifactsByChecksumConstraint( "a1b2c3aksjhdasfkdasasd" );
        List<ArchivaArtifact> artifacts = new ArrayList<ArchivaArtifact>();
        ArchivaArtifact artifact = new ArchivaArtifact( ARCHIVA_TEST_GROUP_ID, ARCHIVA_TEST_ARTIFACT_ID, "1.0", "", "jar", "test-repo"  );
        artifact.getModel().setWhenGathered( whenGathered );
        artifacts.add( artifact );
        
        archivaDAOControl.expectAndReturn( archivaDAO.getArtifactDAO(), artifactDAO );
        artifactDAO.queryArtifacts( constraint );
        artifactDAOControl.setMatcher( MockControl.ALWAYS_MATCHER );
        artifactDAOControl.setReturnValue( artifacts );
        
        archivaDAOControl.replay();
        artifactDAOControl.replay();
        
        List<Artifact> results = searchService.getArtifactByChecksum( "a1b2c3aksjhdasfkdasasd" );
        
        archivaDAOControl.verify();
        artifactDAOControl.verify();
        
        assertNotNull( results );
        assertEquals( 1, results.size() );
    }
    
/* query artifact versions */
    
    public void testGetArtifactVersionsArtifactExists()
        throws Exception
    {
        Date whenGathered = new Date();
        
        List<String> observableRepoIds = new ArrayList<String>();
        observableRepoIds.add( "repo1.mirror" );
        observableRepoIds.add( "public.releases" );
        
        List<String> versions = new ArrayList<String>();
        versions.add( "1.0" );
        versions.add( "1.1-beta-1" );
        versions.add( "1.1-beta-2" );
        versions.add( "1.1" );
        versions.add( "1.2" );
        versions.add( "1.2.1-SNAPSHOT" );
        
        BrowsingResults results = new BrowsingResults( ARCHIVA_TEST_GROUP_ID, ARCHIVA_TEST_ARTIFACT_ID );
        results.setSelectedRepositoryIds( observableRepoIds );
        results.setVersions( versions );
        
        List<ArchivaArtifact> archivaArtifacts = new ArrayList<ArchivaArtifact>();
        ArchivaArtifact archivaArtifact = new ArchivaArtifact( ARCHIVA_TEST_GROUP_ID, ARCHIVA_TEST_ARTIFACT_ID, versions.get( 0 ), "", "pom", "repo1.mirror" );
        archivaArtifact.getModel().setWhenGathered( whenGathered );
        archivaArtifacts.add( archivaArtifact );
        
        archivaArtifact = new ArchivaArtifact( ARCHIVA_TEST_GROUP_ID, ARCHIVA_TEST_ARTIFACT_ID, versions.get( 1 ), "", "pom", "public.releases" );
        archivaArtifact.getModel().setWhenGathered( whenGathered );
        archivaArtifacts.add( archivaArtifact );
        
        archivaArtifact = new ArchivaArtifact( ARCHIVA_TEST_GROUP_ID, ARCHIVA_TEST_ARTIFACT_ID, versions.get( 2 ), "", "pom", "repo1.mirror" );
        archivaArtifact.getModel().setWhenGathered( whenGathered );
        archivaArtifacts.add( archivaArtifact );
        
        archivaArtifact = new ArchivaArtifact( ARCHIVA_TEST_GROUP_ID, ARCHIVA_TEST_ARTIFACT_ID, versions.get( 3 ), "", "pom", "public.releases" );
        archivaArtifact.getModel().setWhenGathered( whenGathered );
        archivaArtifacts.add( archivaArtifact );
        
        archivaArtifact = new ArchivaArtifact( ARCHIVA_TEST_GROUP_ID, ARCHIVA_TEST_ARTIFACT_ID, versions.get( 4 ), "", "pom", "repo1.mirror" );
        archivaArtifact.getModel().setWhenGathered( whenGathered );
        archivaArtifacts.add( archivaArtifact );
        
        archivaArtifact = new ArchivaArtifact( ARCHIVA_TEST_GROUP_ID, ARCHIVA_TEST_ARTIFACT_ID, versions.get( 5 ), "", "pom", "public.releases" );
        archivaArtifact.getModel().setWhenGathered( whenGathered );
        archivaArtifacts.add( archivaArtifact );
        
        userReposControl.expectAndReturn( userRepos.getObservableRepositories(), observableRepoIds );
        repoBrowsingControl.expectAndReturn( repoBrowsing.selectArtifactId( "", observableRepoIds, ARCHIVA_TEST_GROUP_ID,
                                                                            ARCHIVA_TEST_ARTIFACT_ID ), results );
        archivaDAOControl.expectAndReturn( archivaDAO.getArtifactDAO(), artifactDAO );
        
        artifactDAOControl.expectAndDefaultReturn( artifactDAO.getArtifact( ARCHIVA_TEST_GROUP_ID,
                                                                            ARCHIVA_TEST_ARTIFACT_ID, versions.get( 0 ), "", "pom", "repo1.mirror" ),  archivaArtifacts.get( 0 ) );
        artifactDAOControl.expectAndDefaultReturn( artifactDAO.getArtifact( ARCHIVA_TEST_GROUP_ID,
                                                                            ARCHIVA_TEST_ARTIFACT_ID, versions.get( 1 ), "", "pom", "public.releases" ),  archivaArtifacts.get( 1 ) );
        artifactDAOControl.expectAndDefaultReturn( artifactDAO.getArtifact( ARCHIVA_TEST_GROUP_ID,
                                                                            ARCHIVA_TEST_ARTIFACT_ID, versions.get( 2 ), "", "pom", "repo1.mirror" ),  archivaArtifacts.get( 2 ) );
        artifactDAOControl.expectAndDefaultReturn( artifactDAO.getArtifact( ARCHIVA_TEST_GROUP_ID,
                                                                            ARCHIVA_TEST_ARTIFACT_ID, versions.get( 3 ), "", "pom", "public.releases" ),  archivaArtifacts.get( 3 ) );
        artifactDAOControl.expectAndDefaultReturn( artifactDAO.getArtifact( ARCHIVA_TEST_GROUP_ID,
                                                                            ARCHIVA_TEST_ARTIFACT_ID, versions.get( 4 ), "", "pom", "repo1.mirror" ),  archivaArtifacts.get( 4 ) );
        artifactDAOControl.expectAndDefaultReturn( artifactDAO.getArtifact( ARCHIVA_TEST_GROUP_ID,
                                                                            ARCHIVA_TEST_ARTIFACT_ID, versions.get( 5 ), "", "pom", "public.releases" ),  archivaArtifacts.get( 5 ) );
        
        userReposControl.replay();
        repoBrowsingControl.replay();
        artifactDAOControl.replay();
        
        List<Artifact> artifacts = searchService.getArtifactVersions( ARCHIVA_TEST_GROUP_ID, ARCHIVA_TEST_ARTIFACT_ID );
        
        userReposControl.verify();
        repoBrowsingControl.verify();
        artifactDAOControl.verify();
        
        assertNotNull( artifacts );
        assertEquals( 6, artifacts.size() );
    }
    
/* query artifact versions since a given date */
    
    public void testGetArtifactVersionsByDateArtifactExists()
        throws Exception
    {
    
    }
    
    public void testGetArtifactVersionsByDateArtifactDoesNotExist()
        throws Exception
    {
    
    }
    
/* query artifact dependencies */
    
    public void testGetDependenciesArtifactExists()
        throws Exception
    {   
        List<String> observableRepoIds = new ArrayList<String>();
        observableRepoIds.add( "repo1.mirror" );
        observableRepoIds.add( "public.releases" );
        
        ArchivaProjectModel model = new ArchivaProjectModel();
        model.setGroupId( ARCHIVA_TEST_GROUP_ID );
        model.setArtifactId( ARCHIVA_TEST_ARTIFACT_ID );
        model.setVersion( "1.0" );
        
        org.apache.maven.archiva.model.Dependency dependency = new org.apache.maven.archiva.model.Dependency();
        dependency.setGroupId( "commons-logging" );
        dependency.setArtifactId( "commons-logging" );
        dependency.setVersion( "2.0" );
        
        model.addDependency( dependency );
        
        dependency = new org.apache.maven.archiva.model.Dependency();
        dependency.setGroupId( "junit" );
        dependency.setArtifactId( "junit" );
        dependency.setVersion( "2.4" );
        dependency.setScope( "test" );
        
        model.addDependency( dependency );
        
        userReposControl.expectAndReturn( userRepos.getObservableRepositories(), observableRepoIds );        
        repoBrowsingControl.expectAndReturn( 
                 repoBrowsing.selectVersion( "", observableRepoIds, ARCHIVA_TEST_GROUP_ID, ARCHIVA_TEST_ARTIFACT_ID, "1.0" ), model );
        
        repoBrowsingControl.replay(); 
        userReposControl.replay();
        
        List<Dependency> dependencies = searchService.getDependencies( ARCHIVA_TEST_GROUP_ID, ARCHIVA_TEST_ARTIFACT_ID, "1.0" );
        
        repoBrowsingControl.verify();
        userReposControl.verify();
        
        assertNotNull( dependencies );
        assertEquals( 2, dependencies.size() );
    }
    
    public void testGetDependenciesArtifactDoesNotExist()
        throws Exception
    {
        List<String> observableRepoIds = new ArrayList<String>();
        observableRepoIds.add( "repo1.mirror" );
        observableRepoIds.add( "public.releases" );
        
        userReposControl.expectAndReturn( userRepos.getObservableRepositories(), observableRepoIds );
        repoBrowsingControl.expectAndThrow( 
               repoBrowsing.selectVersion( "", observableRepoIds, ARCHIVA_TEST_GROUP_ID, ARCHIVA_TEST_ARTIFACT_ID, "1.0" ), new ObjectNotFoundException( "Artifact does not exist." ) );
        
        userReposControl.replay();
        repoBrowsingControl.replay();
        
        try
        {
            searchService.getDependencies( ARCHIVA_TEST_GROUP_ID, ARCHIVA_TEST_ARTIFACT_ID, "1.0" );
            fail( "An exception should have been thrown." );
        }
        catch ( Exception e )
        {
            assertEquals( "Artifact does not exist.", e.getMessage() );
        }
        
        userReposControl.verify();
        repoBrowsingControl.verify();
    }
    
    public void testGetDependencyTreeArtifactExists()
        throws Exception
    {
        // TODO
    }
    
    public void testGetDependencyTreeArtifactDoesNotExist()
        throws Exception
    {
        // TODO
    }
    
    public void testGetDependees()
        throws Exception
    {
        Date date = new Date();
        List<String> observableRepoIds = new ArrayList<String>();
        observableRepoIds.add( "repo1.mirror" );
        observableRepoIds.add( "public.releases" );
        
        List<ArchivaProjectModel> dependeeModels = new ArrayList<ArchivaProjectModel>();
        ArchivaProjectModel dependeeModel = new ArchivaProjectModel();
        dependeeModel.setGroupId( ARCHIVA_TEST_GROUP_ID );
        dependeeModel.setArtifactId( "archiva-dependee-one" );
        dependeeModel.setVersion( "1.0" );
        dependeeModel.setWhenIndexed( date );
        dependeeModels.add( dependeeModel );
        
        dependeeModel = new ArchivaProjectModel();
        dependeeModel.setGroupId( ARCHIVA_TEST_GROUP_ID );
        dependeeModel.setArtifactId( "archiva-dependee-two" );
        dependeeModel.setVersion( "1.0" );
        dependeeModel.setWhenIndexed( date );
        dependeeModels.add( dependeeModel );
        
        userReposControl.expectAndReturn( userRepos.getObservableRepositories(), observableRepoIds );
        repoBrowsingControl.expectAndReturn( repoBrowsing.getUsedBy( "", observableRepoIds, ARCHIVA_TEST_GROUP_ID,
                                                                     ARCHIVA_TEST_ARTIFACT_ID, "1.0" ), dependeeModels );
        
        repoBrowsingControl.replay(); 
        userReposControl.replay();

        List<Artifact> dependees = searchService.getDependees( ARCHIVA_TEST_GROUP_ID, ARCHIVA_TEST_ARTIFACT_ID, "1.0" );
        
        repoBrowsingControl.verify();
        userReposControl.verify();
        
        assertNotNull( dependees );
        assertEquals( 2, dependees.size() );
    }
    
    public void testGetDependeesArtifactDoesNotExist()
        throws Exception
    {
        Date date = new Date();
        List<String> observableRepoIds = new ArrayList<String>();
        observableRepoIds.add( "repo1.mirror" );
        observableRepoIds.add( "public.releases" );
        
        List dependeeModels = new ArrayList();
        ArchivaProjectModel dependeeModel = new ArchivaProjectModel();
        dependeeModel.setGroupId( ARCHIVA_TEST_GROUP_ID );
        dependeeModel.setArtifactId( "archiva-dependee-one" );
        dependeeModel.setVersion( "1.0" );
        dependeeModel.setWhenIndexed( date );
        dependeeModels.add( dependeeModel );
        
        dependeeModel = new ArchivaProjectModel();
        dependeeModel.setGroupId( ARCHIVA_TEST_GROUP_ID );
        dependeeModel.setArtifactId( "archiva-dependee-two" );
        dependeeModel.setVersion( "1.0" );
        dependeeModel.setWhenIndexed( date );
        dependeeModels.add( dependeeModel );
        
        userReposControl.expectAndReturn( userRepos.getObservableRepositories(), observableRepoIds );
        repoBrowsingControl.expectAndReturn( repoBrowsing.getUsedBy( "", observableRepoIds, ARCHIVA_TEST_GROUP_ID,
                                                                     ARCHIVA_TEST_ARTIFACT_ID, "1.0" ), null );
        
        repoBrowsingControl.replay(); 
        userReposControl.replay();

        try
        {
            List<Artifact> dependees = searchService.getDependees( ARCHIVA_TEST_GROUP_ID, ARCHIVA_TEST_ARTIFACT_ID, "1.0" );
            fail( "An exception should have been thrown." );
        }
        catch ( Exception e )
        {
            repoBrowsingControl.verify();
            userReposControl.verify();
        }
    }
}