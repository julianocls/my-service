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
## Configurando o Vault

Criar estrutura do Vault

Escolha um diretório (exemplo):
``` bash
mkdir -p ~/vault-local/{config,data}
cd ~/vault-local
```

Criar arquivo de configuração

Crie config/vault.hcl:

```
storage "file" {
path = "/Users/SEU_USUARIO/vault-local/data"
}

listener "tcp" {
address     = "127.0.0.1:8200"
tls_disable = 1
}

ui = true
```

Importante
Troque /Users/SEU_USUARIO pelo seu usuário real do macOS
(ou use caminho absoluto equivalente)

Subir o Vault
``` bash
vault server -config=config/vault.hcl
```

Deixe esse terminal aberto.

Configurar variáveis de ambiente (novo terminal)
``` bash
export VAULT_ADDR="http://127.0.0.1:8200"
```

Inicializar o Vault (uma única vez)
``` bash
vault operator init
```

Você receberá:

5 unseal keys
1 root token

Guarde isso com cuidado (nem versiona, nem perde).
Unseal (toda vez que subir o Vault)

Execute 3 vezes (com chaves diferentes):
``` bash
vault operator unseal
```

Após isso:
``` bash
vault status
```

Deve mostrar:
```
Sealed: false
```

Login no Vault
``` bash
vault login
```

Cole o root token.

Habilitar KV (uma vez)
``` bash
vault secrets enable -path=secret kv-v2
```

Criar secrets (persistentes agora!)
``` bash
vault kv put secret/my-service \
db_user=admin \
db_pass=123456 \
api_key=abcdef
```

Ler:

``` bash
vault kv get secret/my-service
```

👉 Agora você pode:
Parar o Vault
Reiniciar
Os secrets continuam lá

----------------------------------------------------------------------
## Configurando o Helm

Depois de instalar o Helm corretamente


``` bash
helm repo add hashicorp https://helm.releases.hashicorp.com
helm repo update
```

E continua com:

``` bash
helm install vault hashicorp/vault \
--namespace vault \
--set injector.enabled=true \
--set server.enabled=false
```


Não estamos subindo Vault server no cluster

Apenas o Vault Agent Injector

Verificar
``` bash
kubectl get pods -n vault
```

Você deve ver:
vault-agent-injector-xxxxx   Running
Configurar Auth Kubernetes no Vault (lado Vault)
Agora vamos permitir que o namespace autentique no Vault.

Habilitar auth Kubernetes
``` bash
vault auth enable kubernetes
```

Copiar o certificado do cluster para se ambiente local:
```` bash
 kubectl config view --raw --minify --flatten \
-o jsonpath='{.clusters[0].cluster.certificate-authority-data}' \
| base64 --decode > /var/run/secrets/kubernetes.io/serviceaccount/ca.crt
````

Configurar o auth
``` bash
vault write auth/kubernetes/config \
token_reviewer_jwt="$(kubectl -n vault get secret \
$(kubectl -n vault get sa vault -o jsonpath='{.secrets[0].name}') \
-o jsonpath='{.data.token}' | base64 --decode)" \
kubernetes_host="https://$(kubectl get svc kubernetes \
-o jsonpath='{.spec.clusterIP}'):443"
```

📌 Isso conecta Vault ↔ Kubernetes API.

Criar Policy no Vault (somente leitura)
```
# my-service-policy.hcl
path "secret/data/my-service" {
capabilities = ["read"]
}
```

``` bash
vault policy write my-service my-service-policy.hcl
```

Criar Role ligada ao Namespace
``` bash
vault write auth/kubernetes/role/my-service \
bound_service_account_names=my-service-sa \
bound_service_account_namespaces=my-service \
policies=my-service \
ttl=24h
```

📌 Regra clara:

Só o namespace my-service
Só o service account my-service-sa
Só leitura

Criar namespace + ServiceAccount da app
``` bash
kubectl create namespace my-service
```

``` yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: my-service-sa
  namespace: my-service
```

``` bash
kubectl apply -f sa.yaml
```

Secrets no Vault (formato ENV)

Aqui está o ponto chave do que você quer 👇
```
vault kv put secret/my-service \
SPRING_DATASOURCE_USERNAME=admin \
SPRING_DATASOURCE_PASSWORD=123456 \
APP_API_KEY=abcdef
```

Tudo em MAIÚSCULO, pronto para virar env var.

Deployment com injeção via memória
Annotations mágicas 🎯
``` yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-service
  namespace: my-service
spec:
  replicas: 1
  selector:
    matchLabels:
      app: my-service
  template:
    metadata:
      labels:
        app: my-service
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
    spec:
      serviceAccountName: my-service-sa
      containers:
        - name: app
          image: my-service:0.1
          command: ["/bin/sh", "-c"]
          args:
            - |
              source /vault/secrets/env && java -jar my-app.jar
```

🔥 O que acontece aqui:

Vault Agent roda como sidecar

Injeta secrets em memória

Cria /vault/secrets/env

O container faz source

As variáveis entram no processo Java

Spring Boot (zero configuração especial)

No Spring:

spring:
datasource:
url: jdbc:postgresql://postgres:5432/mydb


O Spring automaticamente lê:

SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD

✔ Sem saber que existe Vault
✔ Totalmente desacoplado

🔍 Validar dentro do Pod
``` bash
kubectl exec -n my-service -it pod/my-service -- env | grep SPRING
```
