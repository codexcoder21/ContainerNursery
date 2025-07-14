# ContainerNursery
Puts Docker Containers to sleep and wakes them back up when they're needed
This project implements a web server that acts as a proxy, dynamically managing containers based on incoming requests. By default it interacts with Docker, but the container runtime can be swapped by providing a different `ContainerFactory`. The server reads a configuration file to map domain names to specific Docker images, starts these images, and forwards requests to the running containers. It also includes a "keep-warm" mechanism to shut down inactive containers.

## Configuration (`config.json`)

The server's behavior is defined by a json configuration file, specified as an additional argument. This file specifies a list of routes, each mapping a domain to a Docker image and its associated settings.

Example `config.json`:
```json
{
  "routes": [
    {
      "domain": "www.helloworld.com",
      "image": "dockerlocal://hello-world-docker-image:latest",
      "keepWarmSeconds": 30,
      "port": 8080,
      "type": "http"
    }
  ]
}
```

### Route Configuration Fields:

*   `domain` (String): The domain name that this route will handle (e.g., `www.helloworld.com`).
*   `image` (String): Image reference prefixed with a protocol. For example `dockerlocal://hello-world-docker-image:latest` loads an image already present locally. Other protocols include `dockerremote://` for pulling from a registry and `dockerfile://` for loading from an exported image file.
*   `keepWarmSeconds` (Long): The duration in seconds after which an inactive Docker container for this domain will be shut down. If there are no incoming requests for a container within this period, it will be stopped and removed.
*   `port` (Int): The external port that the proxy server listens on for this route.
*   `type` (String): Connection type, one of `http`, `tcp`, or `udp`. `http` routes are handled by the web server; `tcp` and `udp` routes listen on the specified port and proxy raw traffic to the container.

## Server Behavior

The server operates as follows:

1.  **Dynamic Container Management**: When a request arrives for a configured domain, the server checks if a Docker container for that domain is already running.
    *   If a container is active, the request is forwarded to it.
    *   If no container is active, a new Docker container is started using the specified image.
2.  **Proxying**: The server acts as a reverse proxy, forwarding incoming HTTP requests to the appropriate Docker container and relaying the container's response back to the client.
3.  **Port Mapping**: The server automatically exposes the container's internal port (assumed to be `8080`) on a dynamically assigned host port and proxies traffic to it. The external port that clients connect to is specified by the `port` field.
4.  **Keep-Warm Mechanism**: To conserve resources, containers are automatically shut down if they receive no requests for the duration specified by `keepWarmSeconds`. A background task periodically checks for inactive containers and stops them.
5.  **Runtime**: The server uses the default Docker runtime for container execution. You can supply a custom `ContainerFactory` when constructing `ContainerNursery` if you want to manage containers without Docker.

## Running the Application

To run the application:

1.  **Build the project**:
    ```bash
    ./gradlew shadowJar
    ```
2.  **Run the JAR**:
    ```bash
    java -jar build/libs/ContainerNursery-all.jar <path/to/your/config.json>
    ```
    Replace `<path/to/your/config.json>` with the actual path to your configuration file.
    ```
    The server will start on port 8080 by default. Ensure your Docker daemon is running.

## TODO:
* Maximum concurrency limits for each instance of the docker image
* Error reporting if the docker image never becomes available




