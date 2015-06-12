maven-mongodb-plugin
=======================
[![dependencies](https://www.versioneye.com/user/projects/557a20a86165340022000001/badge.svg?style=flat)](https://www.versioneye.com/user/projects/557a20a86165340022000001)

Maven plugin wrapper for the [flapdoodle.de embedded MongoDB API](http://github.com/flapdoodle-oss/embedmongo.flapdoodle.de).

This plugin lets you start and stop an instance of MongoDB during a Maven build, e.g. for integration testing. The Mongo instance isn't strictly embedded (it's not running within the JVM of your application), but it _is_ a managed instance that exists only for the lifetime of your build.

Usage
-----

```xml
<plugin>
    <groupId>com.syncleus.maven.plugins</groupId>
    <artifactId>maven-mongodb-plugin</artifactId>
    <version>1.0.0</version>
    <executions>
        <execution>
            <id>start</id>
            <goals>
                <goal>start</goal>
            </goals>
            <configuration>
                <port>37017</port>
                <!-- optional, default 27017 -->

                <randomPort>true</randomPort>
                <!-- optional, default is false, if true allocates a random port and overrides embedmongo.port -->

                <version>2.0.4</version>
                <!-- optional, default 3.0.3 -->

                <databaseDirectory>/tmp/mongotest</databaseDirectory>
                <!-- optional, default is a new dir in java.io.tmpdir -->

                <logging>file</logging>
                <!-- optional (file|console|none), default console -->

                <logFile>${project.build.directory}/myfile.log</logFile>
                <!-- optional, can be used when logging=file, default is ./mongod.log -->

                <logFileEncoding>utf-8</logFileEncoding>
                <!-- optional, can be used when logging=file, default is utf-8 -->

                <proxyHost>myproxy.company.com</proxyHost>
                <!-- optional, default is none -->

                <proxyPort>8080</proxyPort>
                <!-- optional, default 80 -->

                <proxyUser>joebloggs</proxyUser>
                <!-- optional, default is none -->

                <proxyPassword>pa55w0rd</proxyPassword>
                <!-- optional, default is none -->

                <bindIp>127.0.0.1</bindIp>
                <!-- optional, default is to listen on all interfaces -->

                <downloadPath>http://internal-mongo-repo/</downloadPath>
                <!-- optional, default is http://fastdl.mongodb.org/ -->

                <skip>false</skip>
                <!-- optional, skips this plugin entirely, use on the command line like -Dembedmongo.skip -->
            </configuration>
        </execution>
        <execution>
            <id>stop</id>
            <goals>
                <goal>stop</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Notes
-----

* By default, the `start` goal is bound to `pre-integration-test`, the `stop` goal is bound to `post-integration-test`. You can of course bind to different phases if required.
* If you omit/forget the `stop` goal, any Mongo process spawned by the `start` goal will be stopped when the JVM terminates.
* If you want to run Maven builds in parallel you can use `randomPort` to avoid port conflicts, the value allocated will be available to other plugins in the project as a property `embedmongo.port`.
  If you're using Jenkins, you can also try the [Port Allocator Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Port+Allocator+Plugin).
* If you need to use a proxy to download MongoDB then you can either use `-Dhttp.proxyHost` and `-Dhttp.proxyPort` as additional Maven arguments (this will affect the entire build) or instruct the plugin to use a proxy when downloading Mongo by adding the `proxyHost` and `proxyPort` configuration properties.
* If you're having trouble with Windows firewall rules, try setting the _bindIp_ config property to `127.0.0.1`.
* If you'd like the start goal to start mongodb and wait, you can add `-Dembedmongo.wait` to your Maven command line arguments
