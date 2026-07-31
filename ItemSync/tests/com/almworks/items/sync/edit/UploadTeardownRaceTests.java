package com.almworks.items.sync.edit;

import java.util.concurrent.ExecutionException;

/**
 * Regression test for a teardown ordering bug that surfaced as an intermittent
 * UploadTests.testFailedUpload failure (roughly once per ~25 runs of the full suite).
 *
 * uploadDone() starts the upload-cancel write on a long-event-queue worker thread. If the fixture
 * stops the database before that worker has finished, the write is initiated against a stopped
 * database, throws DatabaseLifecycleException, and is logged at SEVERE - which BaseTestCase turns
 * into "Error log not empty" for whichever test was running.
 *
 * This returns immediately after sendUploadDone(), with no waitForCanUpload and no flushWriteQueue,
 * so the cancel is always still in flight at teardown. That makes the race deterministic rather than
 * occasional: before the fix it failed every run, and it passes only while DatabaseFixture.tearDown
 * waits for the event queue to go idle before stopping the database.
 *
 * Note that draining the write queue does not fix this and is not what makes it pass: the offending
 * write is initiated after stop(), not queued before it.
 */
public class UploadTeardownRaceTests extends SingleAttributeFixture {
  public void testCancelInFlightAtTeardown() throws ExecutionException, InterruptedException {
    long item = createNew("abc");
    TestUploader upload = TestUploader.beginUpload(myManager, item);
    assertNotNull(upload);
    assertTrue(upload.waitStarted());
    upload.sendUploadDone();
    // Deliberately no waitForCanUpload and no flushWriteQueue: return with the cancel still in flight.
  }
}
