#!/bin/bash

# Nerya All Naturals - Docker Deployment Script
# This script helps deploy the application using Docker

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    print_error "Docker is not installed. Please install Docker first."
    exit 1
fi

# Check if Docker Compose is installed
if ! command -v docker-compose &> /dev/null; then
    print_error "Docker Compose is not installed. Please install Docker Compose first."
    exit 1
fi

# Function to build and run with docker-compose
deploy_with_compose() {
    print_status "Building and starting services with Docker Compose..."
    
    # Stop existing containers
    docker-compose down
    
    # Build and start services
    docker-compose up --build -d
    
    print_status "Services started successfully!"
    print_status "Application will be available at: http://localhost:8080"
    print_status "Swagger UI will be available at: http://localhost:8080/swagger-ui"
    print_status "Health check endpoint: http://localhost:8080/actuator/health"
}

# Function to build production image
build_production() {
    print_status "Building production Docker image..."
    
    # Build the application first
    print_status "Building Spring Boot application..."
    ./gradlew bootJar
    
    # Build Docker image
    docker build -f Dockerfile.prod -t nerya-all-naturals:latest .
    
    print_status "Production image built successfully!"
    print_status "Image name: nerya-all-naturals:latest"
}

# Function to run production container
run_production() {
    print_status "Running production container..."
    
    # Stop existing container if running
    docker stop nerya-app-prod 2>/dev/null || true
    docker rm nerya-app-prod 2>/dev/null || true
    
    # Run the container
    docker run -d \
        --name nerya-app-prod \
        -p 8080:8080 \
        -e SPRING_PROFILES_ACTIVE=docker \
        -e SPRING_DATASOURCE_URL="jdbc:mysql://host.docker.internal:3306/nerya?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC" \
        -e SPRING_DATASOURCE_USERNAME=nerya \
        -e SPRING_DATASOURCE_PASSWORD=change-me \
        nerya-all-naturals:latest
    
    print_status "Production container started successfully!"
    print_status "Application will be available at: http://localhost:8080"
}

# Main script logic
case "${1:-deploy}" in
    "deploy")
        deploy_with_compose
        ;;
    "build")
        build_production
        ;;
    "run")
        run_production
        ;;
    "stop")
        print_status "Stopping all containers..."
        docker-compose down
        docker stop nerya-app-prod 2>/dev/null || true
        docker rm nerya-app-prod 2>/dev/null || true
        print_status "All containers stopped!"
        ;;
    "logs")
        if [ "${2:-}" = "prod" ]; then
            docker logs -f nerya-app-prod
        else
            docker-compose logs -f
        fi
        ;;
    "help"|"-h"|"--help")
        echo "Usage: $0 [command]"
        echo ""
        echo "Commands:"
        echo "  deploy    - Build and start services with Docker Compose (default)"
        echo "  build     - Build production Docker image"
        echo "  run       - Run production container"
        echo "  stop      - Stop all containers"
        echo "  logs      - Show logs (add 'prod' for production container)"
        echo "  help      - Show this help message"
        ;;
    *)
        print_error "Unknown command: $1"
        echo "Use '$0 help' to see available commands"
        exit 1
        ;;
esac
