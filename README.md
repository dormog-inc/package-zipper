<img align="right" width="290" height="290" src="assets/logo1.svg">


# Package Zipper

A simple online packages dwnloader.
package-zipper is a java project for downloading and streaming java packages and there dependencies as zip files from maven repository.
this project depends on [zipstreamer](https://github.com/scosman/zipstreamer) for streaming all the package dependencies into a zip file. [Demo here](http://package-zipper.herokuapp.com/swagger-ui/index.html).

Highlights include:

- Transitive dependency downloading!
- Low memory: the files are streamed out to the client immediately
- Low CPU: the default server doesn't compress files, only packages them into a zip, so there's minimal CPU load (configurable)
- High concurrency: the two properties above allow a single small server to stream hundreds of large zips simultaneous

## Why

This project has been started from a need of downloading java jar files online. 
The thing is that as for today, there is no way of installing java packages transitively. 
From the maven repository it is only possible to download standalone jar files or poms, but there's no way of installing a package with its transitive dependencies. 
Another website, named [download jar](https://jar-download.com/) enables to download a zip of transitive jars by a specific artifact name, but it doesn't install pom, and its jars are flat in a single zip file, while we needed it in a maven `.m2` like hierarchy (the same way Artifactory / Nexus usually consumes dependencies). 
We have decided to write a simple java project that solve this problem.

## Deploy

First go to [here](https://github.com/scosman/zipstreamer), give this project a star and follow the deployment instructions on heroku.
after the deployment take the app url and use it as the `ZIP_STREAMER` env variable.

[![Deploy](https://www.herokucdn.com/deploy/button.svg)](https://heroku.com/deploy)


## Config

These ENV vars can be used to config the server:
- `ZIP_STREAMER` - a url to your [zipstreamer](https://github.com/scosman/zipstreamer) deployment.
