package kamon.stackdriver

import java.io.{ByteArrayInputStream, FileInputStream}

import com.google.api.gax.core.{CredentialsProvider, FixedCredentialsProvider}
import com.google.auth.oauth2.GoogleCredentials
import com.typesafe.config.Config

private[stackdriver] object CredentialsProviderFactory {
  def fromConfig(config: Config): CredentialsProvider = {
    val method = config.getString("auth.method")
    method match {
      case "application-default" =>
        FixedCredentialsProvider.create(GoogleCredentials.getApplicationDefault)
      case "keyfile" =>
        val keyfilePath = config.getString("auth.keyfile-path")
        val credentials = GoogleCredentials.fromStream(new FileInputStream(keyfilePath))
        FixedCredentialsProvider.create(credentials)
      case "data-env-var" =>
        val envVarName = config.getString("auth.data-env-var")
        sys.env
          .get(envVarName)
          .map { credentials =>
            val bytes = credentials.getBytes
            FixedCredentialsProvider.create(GoogleCredentials.fromStream(new ByteArrayInputStream(bytes)))
          }
          .getOrElse(
            throw new IllegalArgumentException(
              s"Environment variable ${envVarName} does not exist. Either export it or change kamon.stackdriver.auth.data-env-var"
            )
          )
    }
  }

}
