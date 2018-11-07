# ledger-wallet-daemon &middot; [![CircleCI](https://circleci.com/gh/LedgerHQ/ledger-wallet-daemon.svg?style=shield)](https://circleci.com/gh/LedgerHQ/ledger-wallet-daemon)


## Updating the libcore

When a new version of the libcore is available, we need to update our bindings.

1. Build the libcore, both for linux and osx. The instructions to do so are available
on the [libcore repo](https://github.com/LedgerHQ/lib-ledger-core/).
FYI, here's what it looks like as of writing this readme:

```bash
# cd to the parent directory of the wallet-daemon
git clone --recurse-submodules https://github.com/LedgerHQ/lib-ledger-core.git
mkdir lib-ledger-core-build
cd lib-ledger-core-build
cmake -DBUILD_TESTS=OFF -DTARGET_JNI=ON ../lib-ledger-core && make
```

2. Do the above step for both linux (on the beta machine as user `ledger`) and on a mac.

3. Copy both .so and .dylib files to `lib-ledger-core-build/core/src` on your personal
machine.

4. From the directory of the wallet-daemon, run `./package_libcore.sh`

5. Push the changes to upstream

6. Add a tag on the new commit with the version of the ledger-core-lib, or the commit
hash if no version was tagged
