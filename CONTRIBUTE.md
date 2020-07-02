Contribute Guide
===

Build app
---

### Product flavors

dimension `deliver` defined for multi package install on devices

- prod  
  for release product
- dev  
  for debug local with application-id suffix `.dev`

```bash
# for build & install dev-deliver apk into devices
$ ./gradlew app:installDevDebug

# for build release product
$ ./gradlew app:buildProdRelease
```
