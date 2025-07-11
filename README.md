# ContainerNursery
Puts Docker Containers to sleep and wakes them back up when they're needed
This project implements a web server that acts as a proxy, dynamically managing Docker containers based on incoming requests. It reads a configuration file to map domain names to specific Docker images, starts these images (using the default Docker runtime), and forwards requests to the running containers. It also includes a "keep-warm" mechanism to shut down inactive containers.

## Configuration (`config.json`)

The server's behavior is defined by a json configuration file, specified as an additional argument. This file specifies a list of routes, each mapping a domain to a Docker image and its associated settings.

Example `config.json`:
```json
{
  "routes": [
    {
      "domain": "www.helloworld.com",
      "image": "hello-world-docker-image:latest",
      "keepWarmSeconds": 30,
      "port": 8080
    }
  ]
}
```

### Route Configuration Fields:

*   `domain` (String): The domain name that this route will handle (e.g., `www.helloworld.com`).
*   `image` (String): The Docker image name and tag to be used for this domain (e.g., `hello-world-docker-image:latest`). This image should be available in your local Docker registry.
*   `keepWarmSeconds` (Long): The duration in seconds after which an inactive Docker container for this domain will be shut down. If there are no incoming requests for a container within this period, it will be stopped and removed.
*   `port` (Int): The internal port that the Docker image listens on. The server will set the `PORT` environment variable inside the container to this value. By default, images are expected to listen on port 8080, but this can be overridden.

## Server Behavior

The server operates as follows:

1.  **Dynamic Container Management**: When a request arrives for a configured domain, the server checks if a Docker container for that domain is already running.
    *   If a container is active, the request is forwarded to it.
    *   If no container is active, a new Docker container is started using the specified image.
2.  **Proxying**: The server acts as a reverse proxy, forwarding incoming HTTP requests to the appropriate Docker container and relaying the container's response back to the client.
3.  **Port Mapping**: The server automatically handles port mapping. The Docker container's internal port (specified by the `port` field in `config.json`) is exposed on a dynamically assigned host port. The server then proxies requests to this host port.
4.  **Keep-Warm Mechanism**: To conserve resources, containers are automatically shut down if they receive no requests for the duration specified by `keepWarmSeconds`. A background task periodically checks for inactive containers and stops them.
5.  **Runtime**: The server uses the default Docker runtime for container execution.

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




