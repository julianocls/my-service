# Guia de Deploy da App em Docker e Kubernetes (Minikube - Hyperkit)

## 1. Pré-requisitos

```bash
  brew install minikube
  brew install kubectl
  brew install kubectx

  brew install fzf
  $(brew --prefix)/opt/fzf/install
    
  brew tap hashicorp/tap
  brew install hashicorp/tap/vault
    
  brew install helm@3
  brew link --force helm@3
```

---

## 2. Iniciar o cluster Minikube (driver Hyperkit)

```bash
  minikube delete
```

```bash
  minikube start --driver=hyperkit --memory=4g --cpus=4
```

Configurando Ingress

``` bash
  minikube addons enable ingress
  kubectl get pods -n ingress-nginx
```

---

## 3. Construir a imagem dentro do Minikube ou build via docker e upload no Minikube

``` bash
  minikube image build -t my-services:0.1 .
```

Verificar se a imagem está dentro do Minikube:

``` bash
  minikube image ls | grep my-services
```

Ou 

## Build da imagem local

``` bash
  docker build -t my-services:0.1 .
```

## Rodar localmente

``` bash
  docker run -p 9999:9999 my-services:0.1
```

Teste container

``` bash
  curl http://localhost:9999/actuator/health
```

Enviar imagem construída localmente para o minikube

``` bash
  minikube image load my-services:0.1
```

---

## 4. Aplicar Deployment, Service e Ingress

Cria os namespace da aplicação

``` bash
  kubectl create namespace my-service
  kubectl create sa my-service-sa -n my-service
  kubectl create namespace vault
```

Aplica configurações ao cluster

``` bash
  kubens my-service
```

```bash  
  kubectl apply -f k8s/
```

---

## 5. Verificar recursos

``` bash
  ##kubens
  kubectl get pods
  kubectl get svc
  kubectl get ingress
  kubectl get endpoints my-services
```

---

## 6. Atualizar /etc/hosts

Obtém linha de configuração:

``` bash
  echo $(minikube ip) myservices.local
```

Adicionar o resultado acima no /etc/hosts
``` bash
  sudo nano /etc/hosts
```

---

## 7. Testar via Ingress

Como o ingress depende do host virtual, o curl deve enviar o header:

``` bash
  curl -H "Host: myservices.local" http://$(minikube ip)/actuator/health
```

Resultado esperado:

``` json
{"status": "UP"}
```

---

## 8. Debug

### Ver logs do app

``` bash
  kubectl get pods                                    
  kubectl logs -f deployment/my-services
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

---

## 9. Teste alternativo (sem ingress)

``` bash
  minikube service my-services --url
```

---

# Configurando o Vault

> - Vault rodando **localmente (macOS)**
> - Kubernetes (Minikube) separado
> - Apenas o **Vault Agent Injector** roda no cluster

---

## 10. Configura o Vault para persistir localmente

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
## 11. Configurar url do vault

``` bash
  echo 'export VAULT_ADDR=http://127.0.0.1:8200' >> ~/.zshrc
  source ~/.zshrc
```

## 12. Subir o Vault

```bash
  vault server -config=config/vault.hcl
```

> **Se aparecer a mensagem abaixo, é um indicativo que deu certo, vá para o passo 13:**  
>  proxy environment: http_proxy="" https_proxy="" no_proxy="" \
> incrementing seal generation: generation=1 \
> no `api_addr` value specified in config or in VAULT_API_ADDR; falling back to detection if possible, but this value should be manually set \
> core: Initializing version history cache for core \
> events: Starting event system \
> core: seal configuration missing, not initialized \
> core: security barrier not initialized 

---

## 13. Inicialização (uma única vez), salvando as keys no arquivo

``` bash
  vault operator init -format=json > vault-init.json
````

Agora você verá no arquivo vault-init.json:
- unseal_keys_b64
- root_token

---

## 14. Unseal (sempre que iniciar)

```bash
  vault operator unseal
  vault operator unseal
  vault operator unseal
```

```bash
  vault status
```

---

## 15. Login

```bash
  vault login
```
---

## 16. Resetar o Vault (ambiente local), caso tenha perdido as keys e token

Pare o Vault (CTRL+C)

Apague os dados persistidos
``` bash
  rm -rf ~/vault-local
  mkdir -p ~/vault-local/{config,data}
```

(ou apague a pasta inteira data)

Suba o Vault novamente
``` bash
  vault server -config=config/vault.hcl
```

Inicialize salvando as keys no arquivo
``` bash
  vault operator init -format=json > vault-init.json
````

Agora você verá no arquivo vault-init.json:
- unseal_keys_b64
- root_token

#### Obs. Se precisou fazer o Reset, volte ao passo 14 após obter as keys e token.

---

## 17. Cria um Secrets Engine (KV v2)

```bash
  vault secrets enable -path=secret kv-v2
```

Cria uma secret na Secrets Engine
```bash
  vault kv put secret/my-service \
SPRING_DATASOURCE_USERNAME=admin \
SPRING_DATASOURCE_PASSWORD=123456 \
APP_API_KEY=abcdef
```

Testa vault criada
``` bash
  vault kv get secret/my-service
```

---

## 18. Subir o Vault Agent Injector no cluster

```bash
  helm repo add hashicorp https://helm.releases.hashicorp.com
  helm repo update

  helm install vault hashicorp/vault \
  --namespace vault \
  --set injector.enabled=true \
  --set server.enabled=false
```

Verifica se foi corretamente criado
``` bash
  kubectl get pods -n vault
```

Você vai ver algo como: `vault-agent-injector-xxxx → Running`

---

## 19. Criar ServiceAccount para o Vault autenticar no cluster

Criar o arquivo k8s/sa.yaml
```
apiVersion: v1
kind: ServiceAccount
metadata:
  name: my-service-sa
  namespace: my-service
```

``` bash
  kubectl apply -f k8s/sa.yml
```

## 20. Criar SA no namespace vault
```bash
  kubectl create sa vault-auth -n vault
```

## 21. Criar token manual, k8s/vault-auth-token.yaml:
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: vault-auth-token
  namespace: vault
  annotations:
    kubernetes.io/service-account.name: vault-auth
type: kubernetes.io/service-account-token
```

``` bash
  kubectl apply -f k8s/vault-auth-token.yml
```

## 22. Obter o JWT do Secret
``` bash
  kubectl -n vault get secret vault-auth-token \
  -o jsonpath='{.data.token}' | base64 --decode
```

Esse é o token que o Vault vai usar para falar com a API do Kubernetes

## 23. Verificar se o Secret foi ligado ao SA
``` bash
  kubectl -n vault describe sa vault-auth
```

Deve listar o secret vault-auth-token

## 24. Configurar auth Kubernetes no Vault

```bash
  vault auth enable kubernetes
```

## 25. Criar CA do cluster (local):
``` bash
  kubectl config view --raw --minify --flatten \
-o jsonpath='{.clusters[0].cluster.certificate-authority-data}' \
| base64 --decode > /tmp/ca.crt
```

## 26. Configurar auth:
``` bash
  vault write auth/kubernetes/config \
token_reviewer_jwt="<TOKEN_DO_PASSO_22>" \
kubernetes_host="$(kubectl config view --raw --minify \
-o jsonpath='{.clusters[0].cluster.server}')" \
kubernetes_ca_cert=@/tmp/ca.crt
```

Aqui fecha Vault ↔ Kubernetes

---

## 27. Criar Policy no Vault 

Arquivo config/my-service-policy.hcl
```yaml
path "secret/data/my-service" {
  capabilities = ["read"]
}

```

```bash
  vault policy write my-service config/my-service-policy.hcl
```

---

## 28. Criar Role Kubernetes no Vault

```bash
  vault write auth/kubernetes/role/my-service \
  bound_service_account_names=my-service-sa \
  bound_service_account_namespaces=my-service \
  policies=my-service \
  ttl=24h
```

---

## 29. Injeção de secrets no Pod 

Injeção como env, k8s/deployment.yml
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

Container
```yaml
command: ["java"]
args: ["-jar", "app.jar"]
```

---

## 30. Observações finais

- Vault **não** acessa `/var/run/secrets/...`
- Secrets **não** vão para etcd
- Spring Boot não conhece Vault
- Tudo fica em memória, por namespace
