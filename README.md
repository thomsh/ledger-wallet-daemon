# ledger-wallet-daemon &middot; [![CircleCI](https://circleci.com/gh/LedgerHQ/ledger-wallet-daemon.svg?style=shield)](https://circleci.com/gh/LedgerHQ/ledger-wallet-daemon)


## Updating the libcore

When a new version of the libcore is available, we need to update our bindings.

Prerequisites: [docker](https://www.docker.com/get-started) and [sbt](https://www.scala-sbt.org/download.html) installed

1. Check out [lib core project](https://github.com/LedgerHQ/lib-ledger-core)
   ```bash
   git clone --recurse-submodules https://github.com/LedgerHQ/lib-ledger-core
   ```

2. Copy `build-jar.sh` and `build-jar-linux.sh` to the lib core project folder
   ```bash
   cp $WALLET_DAEMON_FOLDER/build-jar.sh $LIB_CORE_FOLDER
   cp $WALLET_DAEMON_FOLDER/build-jar-linux.sh $LIB_CORE_FOLDER
   ```

3. `cd` to lib core folder
   ```bash
   cd $LIB_CORE_FOLDER
   ```

4. Run the script `build-jar.sh` with command: `mac`, `linux` or `all`.
   MacOS can build both `mac` and `linux`. Linux can only build `linux`.
   ```bash
   # Build for both mac only. You may want to do this when developing, 
   # it's much faster than build for both mac and linux
   bash build-jar.sh mac

   # Build for linux. Linux build is using docker.
   bash build-jar.sh all

   # Build for both mac and linux
   bash build-jar.sh all

   ```

5. After the build, you find the jar file in `$LIB_CORE_FOLDER/../build-jar/target/scala-<version>/build-jar-<version>.jar`.
   Replace the `ledger-lib-core.jar` in the lib folder with this file.
   ```bash
   mv $LIB_CORE_FOLDER/../build-jar/target/scala-<version>/build-jar-<version>.jar $WALLET_DAEMON_FOLDER/lib/ledger-lib-core.jar
   ```

5. Push the changes to upstream

6. Add a tag on the new commit with the version of the ledger-core-lib, or the commit
hash if no version was tagged
