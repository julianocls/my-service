storage "file" {
  path = "/Users/julianosantos/vault-local/data"
}

listener "tcp" {
  address     = "127.0.0.1:8200"
  tls_disable = 1
}

ui = true
