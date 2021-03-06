# consul4spring
A spring-boot extension aimed at using Consul as a micro service registration

[ ![Build Status](https://travis-ci.org/amirkibbar/plum.svg?branch=master) ](https://travis-ci.org/amirkibbar/plum)

[ ![Download](https://api.bintray.com/packages/amirk/maven/consul4spring/images/download.svg) ](https://bintray.com/amirk/maven/consul4spring/_latestVersion)

# Overview

This library is some glue-code that I needed to write my first micro service. I wanted my micro service to use 
spring-boot and to register itself in a [Consul](https://consul.io/) cluster. This library allows a spring-boot 
application to:

- Automatically register itself as a service in Consul
- Register default properties in the Consul key value store
- Mark custom Consul checks as passed/failed
- Use the Consul distributed lock
- Easily store values in the Consul key value store
- Resolve a DNS SRV record

# Acknowledgement

This library uses [Orbitz Consul Client](https://github.com/OrbitzWorldwide/consul-client) to communicate with Consul
and [dnsjava](http://www.dnsjava.org/) for DNS resolution.

# Sample application

You can see a sample of an application using this library at [plum-sample](https://github.com/amirkibbar/plum-sample).

# Using the library features

## Setup

This library requires Java 8 and works with Consul 0.5.0 or above.

### Setup your gradle project

```
    
    repositories {
        maven { url  "http://dl.bintray.com/amirk/maven" }
        maven { url "https://bintray.com/artifact/download/orbitz/consul-client" }
    }
    
    dependencies {
        compile "ajk.plum:plum:0.2.0"
    }
```

### Define the Consul properties

In your application.yml, or in any other way supported by spring-boot, define the following properties

```

    consul:
      hostname: consul
      httpPort: 8500
      dnsPort: 8600
      serviceId: my-service-id
      serviceName: PrettyName
      tags: some, tags
      heartbeatRate: 900
```

### Load the Consul properties

Load the ConsulProperties from the application.yml with spring-boot's @EnableConfigurationProperties annotation:

```java

    @EnableConfigurationProperties(ConsulProperties.class)
```

### Define the "default properties" (optional)

This library allows you to setup a "default properties" object. This object will be registered in the Consul key value
store when your application starts. This object can be anything, as long as it's possible to serialize and deserialize
it as JSON. To define your default properties object just annotate anything in the classpath with @DefaultProperties:

```java
    
    import ajk.consul4spring.DefaultProperties
    
    @DefaultProperties(version = "some version")
    public class MyProperties {
      ...
    }
```

The "version" property determines the application's configuration version versus the configuration registered in Consul.
If the versions differ the existing configuration already registered in Consul is backed up and the provided
DefaultProperties are registered instead. This mechanism allows both changing configuration values in Consul and an easy
way to upgrade configurations.

The version is a simple text, there's no restriction on what can appear in it. A simple "equals" comparison is run
against the registered configuration version in Consul and the configuration is replaced is it is "not equals" to the
version provided by the application.

### Activate the consul profile

When your application is started with the "consul" spring profile active then this library will register the a 
ConsulTemplate bean, a DistributedLock bean and a DnsResolver. If this profile is not active then these services will
be registered with a "fake" implementation that doesn't use Consul. This allows you to run your application regardless
of the existence of Consul in the environment if you wish.

## Registration as a Consul service

The library will register your application as Consul service and add a heartbeat check for this service. The heartbeat
ID in Consul will be <serviceId>-heartbeat@<service-hostname>:<port>. The library will automatically detect the hostname
on which your service is running and the port.

The heartbeat check in consul is set with a 20 seconds TTL, and a scheduled job in the library updates the heartbeat
every 10 seconds.

## Marking Consul checks

You may want to add some custom health checks to Consul to reflect the status of your service. To do this use the 
CheckService:

```java
    
    public class MyHealthMonitor {
      @Autowire
      private CheckService checkService;
      
      public void myHealthReport() {
        if(something) {
          checkService.pass("something", 2 /* TTL in seconds */, "notes");
        } else {
          checkService.fail("something", 2 /* TTL in seconds */, "notes");
        }
      }
    }
```

## Using the distributed lock

Consul provides a convenient distributed lock mechanism. This library exposes this mechanism in an easy to use way:

```java
    
    public class MyService {
      @Autowire
      private DistributedLock lock;
        
      public void myCriticalCode() {
        String lockId = lock.acquire();
        if (lockId == null) {
          // couldn't obtain the lock - deal with it
          return;
        }
        
        try {
          // critical code
        } finally {
          lock.release(lockId);
        }
      }
    }
```

## Easily store and retrieve values from the Consul key value store

Use the ConsulTemplate to retrieve and store values in the Consul key value store:

```java

    public class MyConsulRepository {
        @Autowire
        private ConsulTemplate consulTemplate;
        
        public void storeSomething(String value) {
          consulTemplate.store("/key", value);
        }
        
        public String readStringValue() {
          return consulTemplate.find("/key");
        }
        
        public MyObject readMyObject() {
          return consulTemplate.findAndConvert(MyObject.class, "/key");
        }
    }
    
```

## Resolve DNS SRV records

Use the DnsResolver to get a "cluster" definition of a service. A cluster definition is a comma separated list of 
host:port. For example, let's say that you want to resolve the location of all the nodes in a RabbitMQ cluster:

```java

    public class MyRabbitUsingClass {
        @Autowire
        private DnsResolver dnsResolver;
        
        public void resolveRabbit() {
            System.out.println(dnsResolver.resolveServiceByName("rabbit");
        }
    }

```

If the name "rabbit" is configured in your DNS, for example in your Consul as a service, and there are 2 nodes in that
cluster: rabbit-1 with IP 10.0.0.10 on port 5672 and rabbit-2 with IP 10.0.0.20 on port 6672, then the result of the
above query would be: 10.0.0.10:5672,10.0.0.20:6672 - according to the SRV record.

# Consul service registration

The library registers your application in consul in 2 ways:

1. As a service with a heartbeat check
2. As a new entry in the key value store

The heartbeat check requires your application to enable scheduling using @EnableScheduling (I'm considering writing a
proper spring-boot plugin that would start everything it needs with something like @EnableConsul and load its 
ConsulProperties automatically, but I'm not there just yet).

The structure of the entry in the key value store is a directory structure that aims at making the service configuration
and metadata be both human and machine readable. The directory structure is:

- serviceName - the name of the service as entered in the consul property consul.serviceName
    - access - the root access folder. This folder contains the access properties of each instance of the application. 
               If, for example, the application is started on 2 different machines then each instance will have an
               a folder under this root access folder.
        - instanceHostname:port - the instance hostname and port on which the application is running, for example: 
          my-server:8080. The value of this key is the access parameters to the application in JSON:
          ```{"hostname":"my-server","password":"6fc3627c-5648-48c4-8e22-92cf8627dc0c","port":"8080","ip":"1.2.3.4","username":"user"}```.
          This information allows other micro services to access and use your application.
    - serviceId - the service ID as entered in the consul property consul.serviceId. The purpose of this folder is to
      hold the current configuration of your application. Since there could be several versions of your application
      running at the same time, you can use a different serviceId for each version, this way newer versions could be
      installed and run while older versions are still running, or you can write some upgrade procedure in the newer
      version that takes the configuration from the previous version and upgrades it.
        - config - the root folder for the configuration of your application
            - current - the current configuration of your application. When you want to change your application
              configuration you're expected to edit this value. It's up to you to read the value from here and to let
              your application know that the value has changed.
            - current-version - the version of the currently registered configuration
        - lock - if you use the distributed lock, then this is where the key used to acquire the lock will be created.

# References

See [JavaDocs](http://amirkibbar.github.io/plum/) for more details