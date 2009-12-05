/*
 * Copyright (C) 2008, Mark Struberg <mark.struberg@yahoo.de>
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Git Development Community nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.simple;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import junit.framework.TestCase;

import org.codehaus.plexus.util.FileUtils;
import org.eclipse.jgit.lib.Commit;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.GitIndex.Entry;
import org.eclipse.jgit.simple.StatusEntry.IndexStatus;
import org.eclipse.jgit.simple.StatusEntry.RepoStatus;
import org.eclipse.jgit.transport.URIish;

/**
 * JUnit test for {@link SimpleRepository} 
 */
public class SimpleRepositoryTest extends TestCase {

    private static final String REPO_ORIGIN = "src/test/resources/org/eclipse/jgit/test/repository";
    private static final String REPO_DEST = "target/trash/repoCopy";
	private static final String REPO_NAME = "target/trash/simpleRepo";
	
	@Override
	public void setUp() throws Exception {
		super.setUp();

        recursiveDelete(new File(REPO_NAME));
        recursiveDelete(new File(REPO_DEST));
        
        recursiveCopy(new File(REPO_ORIGIN), new File(REPO_DEST));
	}

	private void recursiveCopy( File repoOrigin, File repoDest ) throws IOException {
	    FileUtils.copyDirectoryStructure(repoOrigin, repoDest);
    }

    @Override
	protected void tearDown() throws Exception {
		File repoDir = new File(REPO_NAME);
		recursiveDelete(repoDir);

		super.tearDown();
	}

	   /**
     * Recursively delete a directory, failing the test if the delete fails.
     *
     * @param dir
     *            the recursively directory to delete, if present.
     */
    private void recursiveDelete(final File dir) {
        if (!dir.exists()) {
            return;
        }
        final File[] ls = dir.listFiles();
        if (ls != null) {
            for (int k = 0; k < ls.length; k++) {
                final File e = ls[k];
                if (e.isDirectory()) {
                    recursiveDelete(e);
                } else {
                    e.delete();
                }
            }
        }
        dir.delete();
    }

	public void testInit() throws Exception {
		File repoDir = new File(REPO_NAME);
		recursiveDelete(repoDir);
		SimpleRepository srep = SimpleRepository.init(repoDir);
		assertNotNull(srep);
		assertTrue(repoDir.exists());
	}

	/**
	 * Test if init(repoDir) works correct for an exisging .git repo. 
	 * @throws Exception 
	 */
	public void testExistingRepo() throws Exception {
		File repoDir = new File(REPO_NAME);
		recursiveDelete(repoDir);
		
		// create an initial repo
		cloneTestRepository();
		
		// and now setup SimpleRepository for an existing .git repo
		SimpleRepository srep = SimpleRepository.existing(repoDir);
		assertNotNull(srep);
		assertTrue(repoDir.exists());
		
	}

	public void testClone() throws Exception {
		cloneTestRepository();
		File testFile = new File(REPO_NAME, "master.txt");
		assertTrue(testFile.exists());
	}
	
	public void testCheckout() throws Exception {
		SimpleRepository srep = cloneTestRepository();
		
		srep.checkout(null, "A", null);
		File testFile = new File(REPO_NAME, "master.txt");
		assertTrue(testFile.exists());
	}

	public void testAddCommitPush() throws Exception {
		final String fileNameToAdd = "myNewFile.txt";
		final String commitMessage = "test commit";
		SimpleRepository srep = cloneTestRepository();
		
		// first we add a file to the Index
		File fileToAdd = createNewFile(srep, fileNameToAdd, "This File will be added, sic!");

        Entry indexEntry = srep.getRepository().getIndex().getEntry(fileNameToAdd);
        assertNull("hoops, found an entry for " + fileNameToAdd + " already: " + indexEntry, indexEntry);

        srep.add(fileToAdd, false);
        
        srep.getRepository().getIndex().read();
        assertNotNull("hoops, found no entry for " + fileNameToAdd, srep.getRepository().getIndex().getEntry(fileNameToAdd));
        
        // now we commit the Index against the tree
        srep.commit(null, null, commitMessage);
        
        // verify the commit
        Commit lastLocalCommit = srep.getRepository().mapCommit(Constants.HEAD);
        assertNotNull(lastLocalCommit);
        assertEquals(commitMessage, lastLocalCommit.getMessage());
        
        // push it, baby, push it real good :)
        boolean pushOk = srep.push(null, "origin", "master");
        assertTrue(pushOk);

        Repository db = srep.getRepository();

        // verify the push
        Commit lastPushedCommit = db.mapCommit(Constants.HEAD);
        assertNotNull(lastPushedCommit);
        assertEquals(commitMessage, lastPushedCommit.getMessage());
	}

	public void testLsFiles() throws Exception {
		SimpleRepository srep = cloneTestRepository();
		List<LsFileEntry> entries = srep.lsFiles();
		assertNotNull(entries);
		assertFalse(entries.isEmpty());
		for (int i=0; i < entries.size(); i++) {
			System.out.println("LsFileEntry[" + i + "]: " + entries.get(i));
		}
		assertEquals(8, entries.size());
		
		// now lets create a file
		createNewFile(srep, "myNewFile.txt", "lsFiles testcontent");

		entries = srep.lsFiles();
		assertNotNull(entries);
		assertFalse(entries.isEmpty());
		for (int i=0; i < entries.size(); i++) {
			System.out.println("LsFileEntry[" + i + "]: " + entries.get(i));
		}
		assertEquals(9, entries.size());
	}
	
	public void testStatus() throws Exception {
		SimpleRepository srep = cloneTestRepository();
		List<StatusEntry> status = srep.status(false, false);
		assertNotNull(status);
		assertEquals("without changes, status has to return an empty list", 0, status.size());
		
		// now we create a file in the local directory!
		final String fileNameToAdd = "myNewFile.txt";
		File fileToAdd = createNewFile(srep, fileNameToAdd, "This File will be added, sic!");
		status = srep.status(false, false);
		assertNotNull(status);
		assertEquals("status now should contain the added file, thus 1 entry!", 1, status.size());
		assertEquals(IndexStatus.UNTRACKED, status.get(0).getIndexStatus());
		assertEquals(RepoStatus.UNTRACKED, status.get(0).getRepoStatus());
		
		// now we add this file to the Index
		srep.add(fileToAdd, false);
		status = srep.status(false, false);
		assertNotNull(status);
		assertEquals("status now should still contain the added file, but different status!", 1, status.size());
		assertEquals(IndexStatus.ADDED, status.get(0).getIndexStatus());
		assertEquals(RepoStatus.UNTRACKED, status.get(0).getRepoStatus());

		//X TODO test remove
	}
	
	public void testRevList() throws Exception {
		SimpleRepository srep = cloneTestRepository();
		{
			List<String> revisions = srep.revList(null, null, null, null, null, -1);
			assertNotNull(revisions);
			assertTrue(!revisions.isEmpty());
			System.out.println("\nfull revisions");
			for (String rev : revisions) {
				System.out.println(rev);
			}
			assertEquals(21, revisions.size());
		}
		{
			List<String> revisions = srep.revList(null, "ac7e7e44c1885efb472ad54a78327d66bfc4ecef", null, null, null, -1);
			assertNotNull(revisions);
			assertTrue(!revisions.isEmpty());
			System.out.println("\ntail revisions");
			for (String rev : revisions) {
				System.out.println(rev);
			}
			assertEquals(19, revisions.size());
		}
		{
			List<String> revisions = srep.revList(null, "42e4e7c5e507e113ebbb7801b16b52cf867b7ce1", "6e1475206e57110fcef4b92320436c1e9872a322", null, null, -1);
			assertNotNull(revisions);
			assertTrue(!revisions.isEmpty());
			System.out.println("\npart revisions");
			for (String rev : revisions) {
				System.out.println(rev);
			}
			assertEquals(17, revisions.size());
		}
		{
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z"); 
			Date since = sdf.parse("2005-04-07 15:30:13 -0700");
			List<String> revisions = srep.revList(null, null, null, since, null, -1);
			assertNotNull(revisions);
			assertTrue(!revisions.isEmpty());
			System.out.println("\npart revisions");
			for (String rev : revisions) {
				System.out.println(rev);
			}
			assertEquals(3, revisions.size());
		}
		{
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z"); 
			Date since = sdf.parse("2005-04-07 15:30:13 -0700");
			Date until = sdf.parse("2005-04-07 15:31:30 -0700");
			List<String> revisions = srep.revList(null, null, null, since, until, -1);
			assertNotNull(revisions);
			assertTrue(!revisions.isEmpty());
			System.out.println("\npart revisions");
			for (String rev : revisions) {
				System.out.println(rev);
			}
			assertEquals(2, revisions.size());
		}
	}
	
	public void testWhatchanged() throws Exception {
		SimpleRepository srep = cloneTestRepository();
		List<ChangeEntry> revisions = srep.whatchanged(null, null, null, null, null, -1);
		assertNotNull(revisions);
		assertTrue(!revisions.isEmpty());
		for (ChangeEntry ce : revisions) {
			System.out.println(ce);
			System.out.println("commit" + ce.getCommitHash());
			System.out.println("Author: " + ce.getAuthorName() + "<" + ce.getAuthorEmail() + ">");
			System.out.println("AuthorDate: " + ce.getAuthorDate());
			System.out.println("Commit: " + ce.getCommitterName() + "<" + ce.getCommitterEmail() + ">");
			System.out.println("CommitDate: " + ce.getCommitterDate());
			System.out.println("\n" + ce.getSubject());
			System.out.println("\n" + ce.getBody());
		}
	}
	
	private SimpleRepository cloneTestRepository() 
	throws URISyntaxException, IOException {
		File repoDir = new File(REPO_NAME);
		recursiveDelete(repoDir);
		URIish uri = new URIish("file://" + (new File(REPO_DEST)).getAbsolutePath());
		SimpleRepository srep = SimpleRepository.clone(repoDir, "origin", uri, "master", null, null);
		assertNotNull(srep);
		return srep;
	}

	/**
	 * Create a fresh file in the repository
	 * @param srep
	 * @param fileNameToAdd
	 * @param content
	 * @return the freshly created File
	 * @throws IOException
	 */
	private File createNewFile(SimpleRepository srep, String fileNameToAdd, String content)
	throws IOException {
		File fileToAdd = new File(srep.getRepository().getWorkDir(), fileNameToAdd);
		
		assertFalse("File " + fileToAdd.getAbsolutePath() + " already exists!", fileToAdd.exists());
		
		BufferedWriter out = new BufferedWriter(new FileWriter(fileToAdd));
		out.write(content);
		out.close();
		return fileToAdd;
	}
	

}
