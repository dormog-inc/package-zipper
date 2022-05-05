## About

package-zipper is a java project for downloading and streaming java packages and there dependecies as zip files from maven repository.
this project depent on [zipstreamer](https://github.com/scosman/zipstreamer) for streaming all the package dependencies into a zip file.

Highlights include:

 - Transtive dependency downloading!
 - Low memory: the files are streamed out to the client immediately
 - Low CPU: the default server doesn't compress files, only packages them into a zip, so there's minimal CPU load (configurable)
 - High concurrency: the two properties above allow a single small server to stream hundreds of large zips simultaneous

 ## Why

I was struggling with the problem of downloading java packages from maven repository with its transitive dependencies, so I decided to write a simple java project that solve this problem.

## Deploy

First go to [here](https://github.com/scosman/zipstreamer), give this project a star and follow the deployment instructions on heroku.
after the deployment take the app url and use it as the ZIP_STREAMER env.

[![Deploy](https://www.herokucdn.com/deploy/button.svg)](https://heroku.com/deploy)


## Config

These ENV vars can be used to config the server:
 - `ZIP_STREAMER` - a url to your [zipstreamer](https://github.com/scosman/zipstreamer) deployment.
