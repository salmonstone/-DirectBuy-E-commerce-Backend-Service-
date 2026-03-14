pipeline {
    agent any

    environment {
        DOCKERHUB_REPO = 'salmonstone/shopecom'
        IMAGE_TAG      = "${BUILD_NUMBER}"
        K8S_NS         = 'shopecom'
        APP_NAME       = 'shopecom'
    }

    stages {

        stage('📥 Checkout') {
            steps {
                echo '--- Pulling code from GitHub ---'
                git branch: 'main',
                    url: 'https://github.com/salmonstone/-DirectBuy-E-commerce-Backend-Service-.git'
            }
        }

        stage('🔨 Maven Build') {
            steps {
                echo '--- Building JAR ---'
                sh 'mvn clean package -DskipTests -q'
                sh 'ls -lh target/*.jar'
            }
        }

        stage('🐳 Docker Build') {
            steps {
                echo '--- Building Docker image ---'
                sh 'docker build -t ${DOCKERHUB_REPO}:${IMAGE_TAG} .'
                sh 'docker tag ${DOCKERHUB_REPO}:${IMAGE_TAG} ${DOCKERHUB_REPO}:latest'
            }
        }

        stage('🔐 Trivy Security Scan') {
            steps {
                echo '--- Scanning image ---'
                sh '''
                    trivy image \
                        --exit-code 0 \
                        --severity HIGH,CRITICAL \
                        --format table \
                        ${DOCKERHUB_REPO}:${IMAGE_TAG}
                '''
            }
        }

        stage('📤 Push to DockerHub') {
            steps {
                echo '--- Pushing to DockerHub ---'
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-creds',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sh 'echo ${DOCKER_PASS} | docker login -u ${DOCKER_USER} --password-stdin'
                    sh 'docker push ${DOCKERHUB_REPO}:${IMAGE_TAG}'
                    sh 'docker push ${DOCKERHUB_REPO}:latest'
                }
            }
        }

        stage('🏗️ Terraform Apply') {
            steps {
                echo '--- Provisioning AWS infrastructure ---'
                dir('terraform') {
                    sh 'terraform init'
                    sh 'terraform apply -auto-approve'
                }
            }
        }

        stage('☸️ Deploy to EKS') {
            steps {
                echo '--- Deploying to EKS ---'
                withCredentials([
                    string(credentialsId: 'db-password',   variable: 'DB_PASSWORD'),
                    string(credentialsId: 'mail-username', variable: 'MAIL_USERNAME'),
                    string(credentialsId: 'mail-password', variable: 'MAIL_PASSWORD'),
                    string(credentialsId: 'groq-api-key',  variable: 'GROQ_API_KEY')
                ]) {
                    sh 'aws eks update-kubeconfig --region ap-south-1 --name shopecom-cluster'
                    sh 'kubectl create namespace ${K8S_NS} --dry-run=client -o yaml | kubectl apply -f -'
                    sh '''kubectl create secret generic shopecom-secrets \
                            --from-literal=DB_PASSWORD=${DB_PASSWORD} \
                            --from-literal=MAIL_USERNAME=${MAIL_USERNAME} \
                            --from-literal=MAIL_PASSWORD=${MAIL_PASSWORD} \
                            --from-literal=GROQ_API_KEY=${GROQ_API_KEY} \
                            --namespace=${K8S_NS} \
                            --dry-run=client -o yaml | kubectl apply -f -'''
                    sh 'kubectl apply -f k8s/mysql.yaml --namespace=${K8S_NS}'
                    sh 'kubectl apply -f k8s/redis.yaml --namespace=${K8S_NS}'
                    sh 'kubectl rollout status deployment/mysql --namespace=${K8S_NS} --timeout=120s || true'
                    sh "sed -i 's|IMAGE_TAG_PLACEHOLDER|${IMAGE_TAG}|g' k8s/deployment.yaml"
                    sh 'kubectl apply -f k8s/ --namespace=${K8S_NS}'
                    sh 'kubectl rollout status deployment/${APP_NAME} --namespace=${K8S_NS} --timeout=300s'
                }
            }
        }

        stage('✅ Health Check') {
            steps {
                echo '--- Verifying deployment ---'
                sh '''
                    sleep 40
                    URL=$(kubectl get svc shopecom-service \
                        -n ${K8S_NS} \
                        -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
                    echo "App URL: http://${URL}"
                    curl -f http://${URL}/health && echo "✅ APP IS LIVE" || echo "⚠️ Health check failed"
                '''
            }
        }
    }

    post {
        success { echo '✅ ShopEcom deployed successfully!' }
        failure { echo '❌ Deployment failed — check logs above' }
    }
}
