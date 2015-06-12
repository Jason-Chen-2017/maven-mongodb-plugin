/**
 * Copyright: (c) Syncleus, Inc.
 *
 * You may redistribute and modify this source code under the terms and
 * conditions of the Open Source Community License - Type C version 1.0
 * or any later version as published by Syncleus, Inc. at www.syncleus.com.
 * There should be a copy of the license included with this file. If a copy
 * of the license is not included you are granted no right to distribute or
 * otherwise use this file except through a legal and valid license. You
 * should also contact Syncleus, Inc. at the information below if you cannot
 * find a license:
 *
 * Syncleus, Inc.
 * 2604 South 12th Street
 * Philadelphia, PA 19148
 */
package com.syncleus.maven.plugins.mongodb;

import static java.util.Collections.*;

import java.io.File;
import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

import com.syncleus.maven.plugins.mongodb.log.Loggers;
import com.syncleus.maven.plugins.mongodb.log.Loggers.LoggingStyle;

import de.flapdoodle.embed.mongo.Command;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.ArtifactStoreBuilder;
import de.flapdoodle.embed.mongo.config.DownloadConfigBuilder;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.config.RuntimeConfigBuilder;
import de.flapdoodle.embed.mongo.config.Storage;
import de.flapdoodle.embed.mongo.distribution.IFeatureAwareVersion;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.distribution.Versions;
import de.flapdoodle.embed.process.config.IRuntimeConfig;
import de.flapdoodle.embed.process.config.io.ProcessOutput;
import de.flapdoodle.embed.process.config.store.IDownloadConfig;
import de.flapdoodle.embed.process.distribution.Distribution;
import de.flapdoodle.embed.process.distribution.IVersion;
import de.flapdoodle.embed.process.exceptions.DistributionException;
import de.flapdoodle.embed.process.runtime.ICommandLinePostProcessor;
import de.flapdoodle.embed.process.runtime.Network;
import de.flapdoodle.embed.process.store.IArtifactStore;

/**
 * When invoked, this goal starts an instance of mongo. The required binaries
 * are downloaded if no mongo release is found in <code>~/.embedmongo</code>.
 * 
 * @goal start
 * @phase pre-integration-test
 * @see <a
 *      href="http://github.com/flapdoodle-oss/embedmongo.flapdoodle.de">http://github.com/flapdoodle-oss/embedmongo.flapdoodle.de</a>
 */
public class StartMongoMojo extends AbstractMojo {

    private static final String PACKAGE_NAME = StartMongoMojo.class.getPackage().getName();
    public static final String MONGOD_CONTEXT_PROPERTY_NAME = PACKAGE_NAME + ".mongod";

    /**
     * The port MongoDB should run on.
     * 
     * @parameter expression="${mongodb.port}" default-value="27017"
     * @since 1.0.0
     */
    private int port;

    /**
     * Whether a random free port should be used for MongoDB instead of the one
     * specified by {@code port}. If {@code randomPort} is {@code true}, the
     * random port chosen will be available in the Maven project property
     * {@code embedmongo.port}.
     * 
     * @parameter expression="${mongodb.randomPort}" default-value="false"
     * @since 1.0.0
     */
    private boolean randomPort;

    /**
     * The version of MongoDB to run e.g. 2.1.1, 1.6 v1.8.2, V2_0_4,
     * 
     * @parameter expression="${mongodb.version}" default-value="3.0.3"
     * @since 1.0.0
     */
    private String version;

    /**
     * The location of a directory that will hold the MongoDB data files.
     * 
     * @parameter expression="${mongodb.databaseDirectory}"
     * @since 1.0.0
     */
    private File databaseDirectory;

    /**
     * An IP address for the MongoDB instance to be bound to during its
     * execution.
     * 
     * @parameter expression="${mongodb.bindIp}"
     * @since 1.0.0
     */
    private String bindIp;

    /**
     * A proxy hostname to be used when downloading MongoDB distributions.
     * 
     * @parameter expression="${mongodb.proxyHost}"
     * @since 1.0.0
     */
    private String proxyHost;

    /**
     * A proxy port to be used when downloading MongoDB distributions.
     * 
     * @parameter expression="${mongodb.proxyPort}" default-value="80"
     * @since 1.0.0
     */
    private int proxyPort;

    /**
     * Block immediately and wait until MongoDB is explicitly stopped (eg:
     * {@literal <ctrl-c>}). This option makes this goal similar in spirit to
     * something like jetty:run, useful for interactive debugging.
     * 
     * @parameter expression="${mongodb.wait}" default-value="false"
     * @since 1.0.0
     */
    private boolean wait;

    /**
     * @parameter expression="${mongodb.logging}" default-value="console"
     * @since 1.0.0
     */
    private String logging;

    /**
     * @parameter expression="${mongodb.logFile}"
     *            default-value="mongodb.log"
     * @since 1.0.0
     */
    private String logFile;

    /**
     * @parameter expression="${mongodb.logFileEncoding}"
     *            default-value="utf-8"
     * @since 1.0.0
     */
    private String logFileEncoding;

    /**
     * The base URL to be used when downloading MongoDB
     * 
     * @parameter expression="${mongodb.downloadPath}"
     *            default-value="http://fastdl.mongodb.org/"
     * @since 1.0.0
     */
    private String downloadPath;

    /**
     * The proxy user to be used when downloading MongoDB
     * 
     * @parameter expression="${mongodb.proxyUser}"
     * @since 1.0.0
     */
    private String proxyUser;

    /**
     * The proxy password to be used when downloading MondoDB
     * 
     * @parameter expression="${mongodb.proxyPassword}"
     * @since 1.0.0
     */
    private String proxyPassword;

    /**
     * Should authorization be enabled for MongoDB
     * 
     * @parameter expression="${mongodb.authEnabled}" default-value="false"
     */
    private boolean authEnabled;

    /**
     * Sets a value for the --replSet
     *
     * @parameter expression="${mongodb.replSet}"
     */
    private String replSet;

    /**
     * The maven project.
     * 
     * @parameter expression="${project}"
     * @readonly
     */
    private MavenProject project;

    /**
     * @parameter expression="${mongodb.skip}" default-value="false"
     */
    private boolean skip;

    @Override
    @SuppressWarnings("unchecked")
    public void execute() throws MojoExecutionException, MojoFailureException {

        if (skip) {
            getLog().debug("skip=true, not starting mongodb");
            return;
        }
        
        if (this.proxyHost != null && this.proxyHost.length() > 0) {
            this.addProxySelector();
        }

        MongodExecutable executable;
        try {

            final ICommandLinePostProcessor commandLinePostProcessor;
            if (authEnabled) {
                commandLinePostProcessor = new ICommandLinePostProcessor() {
                    @Override
                    public List<String> process(final Distribution distribution, final List<String> args) {
                        args.remove("--noauth");
                        args.add("--auth");
                        return args;
                    }
                };
            } else {
                commandLinePostProcessor = new ICommandLinePostProcessor.Noop();
            }

            IRuntimeConfig runtimeConfig = new RuntimeConfigBuilder()
                    .defaults(Command.MongoD)
                    .processOutput(getOutputConfig())
                    .artifactStore(getArtifactStore())
                    .commandLinePostProcessor(commandLinePostProcessor)
                    .build();

            if (randomPort) {
                port = PortUtils.allocateRandomPort();
            }
            savePortToProjectProperties();

            IMongodConfig config = new MongodConfigBuilder()
                    .version(getVersion()).net(new Net(bindIp, port, Network.localhostIsIPv6()))
                    .replication(new Storage(getDataDirectory(), replSet, 0))
                    .build();

            executable = MongodStarter.getInstance(runtimeConfig).prepare(config);
        } catch (UnknownHostException e) {
            throw new MojoExecutionException("Unable to determine if localhost is ipv6", e);
        } catch (DistributionException e) {
            throw new MojoExecutionException("Failed to download MongoDB distribution: " + e.withDistribution(), e);
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to Config MongoDB: ", e);
        }

        try {
            MongodProcess mongod = executable.start();

            if (wait) {
                while (true) {
                    try {
                        TimeUnit.MINUTES.sleep(5);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }

            getPluginContext().put(MONGOD_CONTEXT_PROPERTY_NAME, mongod);
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to start the mongod", e);
        }
    }

    /**
     * Saves port to the {@link MavenProject#getProperties()} (with the property
     * name {@code mongodb.port}) to allow others (plugins, tests, etc) to
     * find the randomly allocated port.
     */
    private void savePortToProjectProperties() {
        project.getProperties().put("mongodb.port", String.valueOf(port));
    }

    private ProcessOutput getOutputConfig() throws MojoFailureException {

        LoggingStyle loggingStyle = LoggingStyle.valueOf(logging.toUpperCase());

        switch (loggingStyle) {
            case CONSOLE:
                return Loggers.console();
            case FILE:
                return Loggers.file(logFile, logFileEncoding);
            case NONE:
                return Loggers.none();
            default:
                throw new MojoFailureException("Unexpected logging style encountered: \"" + logging + "\" -> " +
                        loggingStyle);
        }

    }

    private IArtifactStore getArtifactStore() {
        IDownloadConfig downloadConfig = new DownloadConfigBuilder().defaultsForCommand(Command.MongoD).downloadPath(downloadPath).build();
        return new ArtifactStoreBuilder().defaults(Command.MongoD).download(downloadConfig).build();
    }

    private void addProxySelector() {

        // Add authenticator with proxyUser and proxyPassword
        if (proxyUser != null && proxyPassword != null) {
            Authenticator.setDefault(new Authenticator() {
                @Override
                public PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(proxyUser, proxyPassword.toCharArray());
                }
            });
        }

        final ProxySelector defaultProxySelector = ProxySelector.getDefault();
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                if (uri.getHost().equals("fastdl.mongodb.org")) {
                    return singletonList(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
                } else {
                    return defaultProxySelector.select(uri);
                }
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            }
        });
    }

    private IFeatureAwareVersion getVersion() {
        String versionEnumName = this.version.toUpperCase().replaceAll("\\.", "_");

        if (versionEnumName.charAt(0) != 'V') {
            versionEnumName = "V" + versionEnumName;
        }

        try {
            return Version.valueOf(versionEnumName);
        } catch (IllegalArgumentException e) {
            getLog().warn("Unrecognised MongoDB version '" + this.version + "', this might be a new version that we don't yet know about. Attemping download anyway...");
            return Versions.withFeatures(new IVersion() {
                @Override
                public String asInDownloadPath() {
                    return version;
                }
            });
        }

    }

    private String getDataDirectory() {
        if (databaseDirectory != null) {
            return databaseDirectory.getAbsolutePath();
        } else {
            return null;
        }
    }

}
