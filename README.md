# Gameon Quarkus Room

A recreation of the GameOn Room service (RecRoom/MugRoom/Basement) using Quarkus, rather than JEE.

# Build instructions. 

`git clone https://github.com/BarDweller/gameon-quarkusroom.git`

If you have graalvm etc locally.. then
`./mvnw clean package -Dnative`

If you don't, and you have Docker (or podman), you can use.. 
`./mvnw clean package -Dnative -Dquarkus.native.container-build=true`

Finally build the container using 
`docker build -t gameontext/quarkus-recroom:1.0 -f src/main/docker/Dockerfile.native .`

(or, if using RancherDesktop for testing.. )
`nerdctl build --namespace k8s.io -t gameontext/quarkus-recroom:1.0 -f src/main/docker/Dockerfile.native .`
