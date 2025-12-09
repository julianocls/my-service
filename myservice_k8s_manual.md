# Guia de Deploy da App em Docker e Kubernetes (Minikube - Hyperkit)

## 1. Pré-requisitos

-   macOS com Homebrew
-   Minikube instalado
-   Hyperkit instalado (`brew install hyperkit`)
-   Kubectl instalado
-   Docker instalado

------------------------------------------------------------------------

## 2. Iniciar o cluster Minikube (driver Hyperkit)

``` bash
minikube delete
minikube start --driver=hyperkit --memory=4g --cpus=4
```

Verifique o IP:

``` bash
minikube ip
```

------------------------------------------------------------------------

## 3. Construir a imagem dentro do Minikube

``` bash
minikube image build -t my-services:0.1 .
```

Verificar se a imagem está dentro do Minikube:

``` bash
minikube image ls | grep my-services
```

------------------------------------------------------------------------

## 4. Aplicar Deployment, Service e Ingress

``` bash
kubectl apply -f k8s/deployment.yml
kubectl apply -f k8s/service.yml
kubectl apply -f k8s/ingress.yml
```

------------------------------------------------------------------------

## 5. Verificar recursos

``` bash
kubectl get pods
kubectl get svc
kubectl get ingress
kubectl get endpoints my-services
```

------------------------------------------------------------------------

## 6. Atualizar /etc/hosts

Adicionar:

    <minikube-ip> myservices.local

Exemplo:

    192.168.64.2 myservices.local

------------------------------------------------------------------------

## 7. Testar via Ingress

Como o ingress depende do host virtual, o curl deve enviar o header:

``` bash
curl -H "Host: myservices.local" http://$(minikube ip)/actuator/health
```

Resultado esperado:

``` json
{"status": "UP"}
```

------------------------------------------------------------------------

## 8. Debug

### Ver logs do app

``` bash
kubectl logs <POD>
```

### Exec busybox para testar acessos internos

``` bash
kubectl run tmp --rm -i --tty --image=busybox -- sh

wget -qO- http://my-services:80/actuator/health
```

### Logs do Ingress

``` bash
kubectl logs -n ingress-nginx deploy/ingress-nginx-controller
```

------------------------------------------------------------------------

## 9. Teste alternativo (sem ingress)

``` bash
minikube service my-services --url
```

------------------------------------------------------------------------

## 10. Conclusão

Fluxo validado com sucesso usando driver Hyperkit e Ingress configurado
corretamente.
