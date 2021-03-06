# Mogwee Push, an APNS Java library

Mogwee Push is a simple library for communicating with Apple's Push Notification Service.  There are two main classes:

- `ApnsSocket` which creates a re-usable socket connection with Apple's development or production push services
- `FeedbackService` which communicates with Apple's development or production feedback services

Please note: the code contains very few Javadocs or really documentation of any sort.  It's devoid of unit tests.  The main reasons to use it other than something like, say, [javapns](http://code.google.com/p/javapns/) are:
1. It's extremely simple (fewer than 1,000 lines, with the real "meat" at around 200 lines).
2. It allows a single server to efficiently send both development and production notifications (unlike javapns, whose singleton design requires re-opening the connection to switch between development and production).


## Maven Info

	<dependency>
		<groupId>com.mogwee</groupId>
		<artifactId>mogwee-push</artifactId>
		<version>1.1.0</version>
	</dependency>


## Apple Push Notification Service Documentation

Before using Mogwee Push, you should have a basic knowledge of how Apple's [Push Notification Service](http://developer.apple.com/library/ios/#documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/ApplePushService/ApplePushService.html) works.

## Generating Your APNS Certificates

As with all things iOS, the most confusing part of setting up push notifications is getting the certificates to play nice with everything else.  The certificates for the Apple Push Notification Service (APNS) expire with some regularity.  Dev certificates expire every 90 days, while Production certs last for a year.  They have to be regenerated and refreshed in the server. 

### Step 1: iOS Provisioning Portal

To generate, first (in Safari) go to [developer.apple.com](http://developer.apple.com) and log into the iOS provisioning portal. Once there, select App IDs in the left-hand menu.  Follow the instructions for enabling Push Notifications.  In the end, you'll be able to download two .cer files: one for production and one for development.

Important: this **must be done in Safari**. It will fail randomly in other browsers.

### Step 2: Keychain

Open those files.  This should launch Keychain and add them to your certificates.

Now on the left-hand list of options, select "Certificates" at the bottom. This is required for the export process to work.

Select the appropriate certificate from the list and right click, then choose 'export'. Then save the file to local disk and upload to server with the appropriate name depending on the cert and the locations you've specified in your code.

On the server, the files should have '400' permissions (i.e., chmod 400) so that only the owner can read them.

And that should do it. A server restart may be required for the new certificate to be picked up.


## Updating Apple's APNS Certificates

Occasionally, the certificates stored in the `apple.keystore` included in this code will expire or change.  You can see the various expiration dates by running:

	keytool -list -v -keystore src/main/resources/apple.keystore -storepass apple | egrep '(Valid|Alias)'

There is a `CreateAppleCertificateKeystore` class in the tests directory that you can run to fetch new certificates.  It will spew out exceptions and ask weird things.  Just keep hitting return, then replace `apple.keystore` with the `/tmp/apple.keystore` file it creates.

Alternatively, you can create your own `SSLContext` and pass that to the `ApnsSocketFactory`.

(At some point, it would be nice for Mogwee Push to automatically fetch new certificates when they expire or change…)


## Push Usage

Constructing an ApnsSocket looks something like:

	final ApnsSocket productionSocket = new ApnsSocket(new ApnsSocketFactory(
		"/path/to/cert/apns.p12",
		"$ooper$3cr3t",
		"PKCS12",
		ApnsSocketFactory.Type.PRODUCTION_PUSH
	));
	final ApnsSocket developmentSocket = new ApnsSocket(new ApnsSocketFactory(
		"/path/to/cert/apns-dev.p12",
		"pa$$w0rd",
		"PKCS12",
		ApnsSocketFactory.Type.DEVELOPMENT_PUSH
	));

For a detailed example of sending a push, see the Javadoc for `ApnsSocket.send(String deviceToken, byte[] payloadBytes, long expiration, TimeUnit expirationUnit)`.


## Feedback Usage

Beware: Apple's feedback service is annoyingly stateful.  It will report a failed device exactly once after its last successful delivery.  Your FeedbackService must be a singleton, and you **should not** expect to get an alert for every message sent to a device that's uninstalled your app.  Also, there's something like a 10 minute delay before Apple reports the failure (presumably while it's retrying).  Which is to say, it's painful to test…

Constructing a FeedbackService looks something like:

	final FeedbackService feedbackService = new FeedbackService(new ApnsSocketFactory(
		"/path/to/cert/apns.p12",
		"$ooper$3cr3t",
		"PKCS12",
		ApnsSocketFactory.Type.PRODUCTION_FEEDBACK
	));
	final FeedbackService developmentFeedbackService = new FeedbackService(new ApnsSocketFactory(
		"/path/to/cert/apns-dev.p12",
		"pa$$w0rd",
		"PKCS12",
		ApnsSocketFactory.Type.DEVELOPMENT_FEEDBACK
	));

Then you want to spin up a thread to process failed tokens.

	LOG.info("Scheduling start of APNS feedback service polling");
	scheduledExecutorService.scheduleWithFixedDelay(new Runnable()
	{
		@Override
		public void run()
		{
			try {
				LOG.info("Polling APNS feedback for production service");
				feedbackService.processFailedTokens(new MyFailedTokenProcessor());
			}
			catch (Exception e) {
				LOG.warn(e, "Failure processing APNS production feedback");
			}
		}
	}, 20, 60, TimeUnit.SECONDS);
	scheduledExecutorService.scheduleWithFixedDelay(new Runnable()
	{
		@Override
		public void run()
		{
			try {
				LOG.info("Polling APNS feedback for development service");
				developmentFeedbackService.processFailedTokens(new MyFailedTokenProcessor());
			}
			catch (Exception e) {
				LOG.warn(e, "Failure processing APNS development feedback");
			}
		}
	}, 50, 60, TimeUnit.SECONDS);

	private class MyFailedTokenProcessor implements FeedbackService.FailedTokenProcessor
	{
		@Override
		public void tokenWithFailure(String deviceToken, ReadableDateTime timestamp)
		{
			try {
				ReadableDateTime lastSeen = getMostRecentRegistrationTime(deviceToken);

				if (lastSeen != null && lastSeen.isBefore(timestamp)) {
					removeToken(deviceToken);
				}
			}
			catch (RuntimeException e) {
				LOG.warnf(e, "Failure processing APNS token: %s", deviceToken);
			}
		}
	}


## Dependencies

Mogwee Push depends on [Joda Time](http://joda-time.sourceforge.net/), [Mogwee Logging](https://github.com/ning/mogwee-logging), and [Mogwee Executors](https://github.com/ning/mogwee-executors).  The first is available in pretty much every Maven repository.  The latter two are available in the [Sonatype Nexus Maven Repository](https://repository.sonatype.org/).


## Build

    mvn install


## Version History

### 1.1.0:
* Updated to use enhanced notification format.  (This is necessary to pick up token failures that the feedback service misses.)
* Added `ApnsSocketFactory` constructor that takes an `SSLContext` (to allow full control over SSL socket creation).
* Updated apple.keystore with latest certificates


## License (see COPYING file for full license)

Copyright 2011 Ning, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.