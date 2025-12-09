# Guia de Deploy da App em Docker e Kubernetes (Minikube)

Este guia detalha como subir a aplicação **my-service** em **Docker** e
depois em **Kubernetes** usando **Minikube**, cobrindo macOS e Linux.
Inclui instruções para ingress, debug e resolução de problemas comuns.

------------------------------------------------------------------------

## 1. Pré-requisitos

-   Docker Desktop ou Docker Engine instalado
-   Minikube instalado
-   kubectl instalado
-   Sudo/admin privileges (necessário para `minikube tunnel`)

------------------------------------------------------------------------

## 2. Instalação do Minikube

### macOS

``` bash
brew install minikube
brew install kubectl
```

### Linux

# Minikube

``` bash
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube
```

# kubectl

``` bash
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
```

------------------------------------------------------------------------

## 3. Inicializando o Minikube

macOS / Linux (Docker driver)

``` bash
minikube start --driver=docker --memory=4g --cpus=4 --disk-size=20g
```

Ajuste memória/CPU se necessário, dependendo da disponibilidade do seu
Docker.

------------------------------------------------------------------------

## 4. Build e execução da aplicação em Docker

``` bash
cd ~/development/workspace-projetos/my-service
```

# Build da imagem local

``` bash
docker build -t my-services:0.1 .
```

# Rodar localmente

``` bash
docker run -p 9999:9999 my-services:0.1
```

# Teste

``` bash
curl http://localhost:9999/actuator/health
```

------------------------------------------------------------------------

## 5. Enviando a imagem para o Minikube (sem registry)

Por ser um ambiente local, o Minikube permite carregar imagens
diretamente, sem necessidade de usar Docker Hub ou ECR.

### **5.1 Construir a imagem diretamente no Minikube**

``` bash
minikube image build -t my-services:0.1 .
```

### **5.2 OU carregar a imagem já construída localmente**

``` bash
minikube image load my-services:0.1
```

No `deployment.yaml`, use a mesma tag:

``` yaml
image: my-services:0.1
imagePullPolicy: IfNotPresent
```

------------------------------------------------------------------------

## 6. Subindo a aplicação no Kubernetes

``` bash
kubectl apply -f k8s/deployment.yml
kubectl apply -f k8s/service.yml
kubectl apply -f k8s/ingress.yml
```

Verificar status:

``` bash
kubectl get pods
kubectl get svc
kubectl get ingress
```

------------------------------------------------------------------------

## 7. Configurando Ingress

``` bash
minikube addons enable ingress
kubectl get pods -n ingress-nginx
sudo minikube tunnel
```

⚠️ Deixe o terminal aberto enquanto o tunnel estiver rodando.\
⚠️ Se travar, verifique se as portas 80 e 443 não estão ocupadas:

``` bash
sudo lsof -i :80
sudo lsof -i :443
```

Adicionar o host no `/etc/hosts`:

    192.168.58.2   myservices.local

Teste no navegador ou curl:

``` bash
curl http://myservices.local/actuator/health
```

------------------------------------------------------------------------

## 8. Debug e problemas comuns

### 8.1 Ping ou curl falham

Normal no macOS com driver Docker; teste dentro da VM:

``` bash
kubectl run tmp --rm -i --tty --image=busybox sh
```

# Dentro do pod:

``` bash
wget -qO- http://my-services:9999/actuator/health
```

### 8.2 HTTP 404 no pod busybox

Certifique-se de usar o caminho correto `/actuator/health`.

### 8.3 Tunnel demora ou não inicia

Verifique portas privilegiadas.\
Desative firewalls/antivírus.\
Certifique-se de que nenhum SSH forwarding está usando 80/443.

------------------------------------------------------------------------

## 9. Acesso alternativo rápido

``` bash
minikube service my-services --url
```

Retorna URL como `http://127.0.0.1:30999`, acessível direto no
navegador.

------------------------------------------------------------------------

## 10. Observações finais

Ingress no macOS com Docker: sempre use `minikube tunnel` ou
`/etc/hosts` + Minikube IP.

Para desenvolvimento rápido, `minikube service` é mais confiável que
ingress.

Logs úteis:

``` bash
kubectl logs -n ingress-nginx deploy/ingress-nginx-controller
kubectl logs <nome-do-pod>
```

Sempre valide o IP do ingress:

``` bash
kubectl get ingress
```
