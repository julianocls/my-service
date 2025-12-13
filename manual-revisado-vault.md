# Guia de Deploy da App em Docker e Kubernetes (Minikube - Hyperkit)

## 1. Pré-requisitos

```bash
brew install minikube
brew install kubectl
brew install kubectx
brew install fzf
$(brew --prefix)/opt/fzf/install

kubectl create namespace my-service
kubectl create sa my-service-sa -n my-service

brew tap hashicorp/tap
brew install hashicorp/tap/vault

kubectl create namespace vault
brew install helm@3
brew link --force helm@3

```

-   macOS com Homebrew
-   Minikube instalado
-   Hyperkit instalado (`brew install hyperkit`)
-   Kubectl instalado
-   Docker instalado

------------------------------------------------------------------------

## 2. Iniciar o cluster Minikube (driver Hyperkit)

```bash
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

------------------------------------------------------------------------

# Configurando o Vault

> - Vault rodando **localmente (macOS)**
> - Kubernetes (Minikube) separado
> - Apenas o **Vault Agent Injector** roda no cluster

---

## Vault Local Persistente

### Estrutura de diretórios

```bash
mkdir -p ~/vault-local/{config,data}
cd ~/vault-local
```

### Configuração (`config/vault.hcl`)

``` bash
sudo nano config/vault.hcl
```

```hcl
storage "file" {
  path = "/Users/julianosantos/vault-local/data"
}

listener "tcp" {
  address     = "127.0.0.1:8200"
  tls_disable = 1
}

ui = true
```

---

## Subir o Vault

```bash
  export VAULT_ADDR=http://127.0.0.1:8200
  vault server -config=config/vault.hcl
```

---

## Inicialização (uma única vez)

```bash
vault operator init
```

Guarde, ex:
- Unseal Keys
- Root Token

Unseal Key 1: yoDNJ2sDp0OyonQ8qnGasWUzz1TK2YUEwl15yvcBRDbp
Unseal Key 2: wBiDT2aB0B6uGCvZMROCW/cMLLJLnXV6ziDqkkEjXZf0
Unseal Key 3: YA2bVTbK85r3j2UnNtyLUpQVUepGCgYOjggSO76/EYpO
Unseal Key 4: eyktxLyGHRxOn9ZXlk944mbK99XQVkc0igsQBgB5/XDi
Unseal Key 5: rnJz0OqlEvTEoEZAYnDrB1pb5t2fe614HoPE6c26KvEq

Initial Root Token: hvs.8xZsVosCNM5kHzBr1YCZTTsB

---

## Unseal (sempre que iniciar)

```bash
vault operator unseal
vault operator unseal
vault operator unseal
```

```bash
vault status
```

---

## Login

```bash
vault login
```

---

## Secrets Engine (KV v2)

```bash
vault secrets enable -path=secret kv-v2
```

```bash
vault kv put secret/my-service \
SPRING_DATASOURCE_USERNAME=admin \
SPRING_DATASOURCE_PASSWORD=123456 \
APP_API_KEY=abcdef
```

---

## Vault Agent Injector (no cluster)

```bash
kubectl create namespace vault
```

```bash
helm repo add hashicorp https://helm.releases.hashicorp.com
helm repo update

helm install vault hashicorp/vault \
  --namespace vault \
  --set injector.enabled=true \
  --set server.enabled=false
```

---

## Auth Kubernetes (Vault fora do cluster)

### ServiceAccount reviewer

```bash
kubectl create sa vault-auth -n vault
kubectl -n vault create token vault-auth
```

### CA do cluster

``` bash
  kubectl apply -f vault-auth-token.yaml
```

### Obter o JWT do Secret
``` bash
  kubectl -n vault get secret vault-auth-token \
  -o jsonpath='{.data.token}' | base64 --decode
```

### Verificar se o Secret foi ligado ao SA
``` bash
  kubectl -n vault describe sa vault-auth
```

### Configurar auth no Vault

```bash
  vault auth enable kubernetes
```

### Agora use esse token no Vault
``` bash
  vault write auth/kubernetes/config \
token_reviewer_jwt="<TOKEN>" \
kubernetes_host="$(kubectl config view --raw --minify -o jsonpath='{.clusters[0].cluster.server}')" \
kubernetes_ca_cert=@/tmp/ca.crt
```

```bash
  kubectl config view --raw --minify --flatten \
-o jsonpath='{.clusters[0].cluster.certificate-authority-data}' \
| base64 --decode > /tmp/ca.crt
```

---

## Policy

```hcl
path "secret/data/my-service" {
  capabilities = ["read"]
}
```

```bash
  vault policy write my-service config/my-service-policy.hcl
```

---

## Role Kubernetes

```bash
vault write auth/kubernetes/role/my-service \
  bound_service_account_names=my-service-sa \
  bound_service_account_namespaces=my-service \
  policies=my-service \
  ttl=24h
```

---

## Injeção de secrets no Pod (em memória)

```yaml
annotations:
  vault.hashicorp.com/agent-inject: "true"
  vault.hashicorp.com/role: "my-service"
  vault.hashicorp.com/agent-inject-secret-env: "secret/data/my-service"
  vault.hashicorp.com/agent-inject-template-env: |
    {{- with secret "secret/data/my-service" -}}
    {{- range $k, $v := .Data.data }}
    export {{ $k }}="{{ $v }}"
    {{- end }}
    {{- end }}
```

```yaml
command: ["/bin/sh", "-c"]
args:
  - |
    source /vault/secrets/env && java -jar app.jar
```

---

## Observações finais

- Vault **não** acessa `/var/run/secrets/...`
- Secrets **não** vão para etcd
- Spring Boot não conhece Vault
- Tudo fica em memória, por namespace
