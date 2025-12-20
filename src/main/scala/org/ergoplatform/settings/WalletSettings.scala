package org.ergoplatform.settings

import org.ergoplatform.nodeView.wallet.WalletProfile
import org.ergoplatform.wallet.settings.SecretStorageSettings

case class WalletSettings(secretStorage: SecretStorageSettings,
                          seedStrengthBits: Int,
                          mnemonicPhraseLanguage: String,
                          usePreEip3Derivation: Boolean = false,
                          keepSpentBoxes: Boolean = false,
                          defaultTransactionFee: Long = 1000000L,
                          dustLimit: Option[Long] = None,
                          maxInputs: Int = 100,
                          optimalInputs: Int = 3,
                          testMnemonic: Option[String] = None,
                          testKeysQty: Option[Int] = None,
                          // Some(Seq(x)) burns all except x, Some(Seq.empty) burns all, None ignores that feature
                          tokensWhitelist: Option[Seq[String]] = None,
                          checkEIP27: Boolean = false,
                          profile: String = WalletProfile.User.label,
                          walletMode: WalletMode = WalletMode.Full) {

  val walletProfile: WalletProfile = WalletProfile.fromLabel(profile)

}

object WalletSettings {
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ValueReader
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import org.ergoplatform.wallet.settings.SecretStorageSettings

  implicit val walletSettingsReader: ValueReader[WalletSettings] = { (cfg, path) =>
    WalletSettings(
      cfg.as[SecretStorageSettings](s"$path.secretStorage"),
      cfg.as[Int](s"$path.seedStrengthBits"),
      cfg.as[String](s"$path.mnemonicPhraseLanguage"),
      cfg.getAs[Boolean](s"$path.usePreEip3Derivation").getOrElse(false),
      cfg.getAs[Boolean](s"$path.keepSpentBoxes").getOrElse(false),
      cfg.getAs[Long](s"$path.defaultTransactionFee").getOrElse(1000000L),
      cfg.getAs[Long](s"$path.dustLimit"),
      cfg.getAs[Int](s"$path.maxInputs").getOrElse(100),
      cfg.getAs[Int](s"$path.optimalInputs").getOrElse(3),
      cfg.getAs[String](s"$path.testMnemonic"),
      cfg.getAs[Int](s"$path.testKeysQty"),
      cfg.getAs[Seq[String]](s"$path.tokensWhitelist"),
      cfg.getAs[Boolean](s"$path.checkEIP27").getOrElse(false),
      cfg.getAs[String](s"$path.profile").getOrElse(WalletProfile.User.label),
      cfg.getAs[String](s"$path.mode").flatMap(WalletMode.fromString).getOrElse(WalletMode.Full)
    )
  }
}
