# Labo-1
Protocoles applicatifs mobiles

## Question Firebase
Le token obtenu avec la méthode onNewToken du Service Firebase de réception des messages est une chaîne de caractères unique qui identifie un appareil particulier auprès des serveurs Firebase Cloud Messaging (FCM). Ce token est généré ou regénéré lorsque l'application s'enregistre pour recevoir des notifications push via le service FCM.

Dans le cas d'un service de messagerie tel que WhatsApp, l'application doit enregistrer chaque appareil de l'utilisateur pour recevoir des notifications push. Lorsqu'un nouvel utilisateur s'inscrit, ou lorsqu'il installe l'application sur un nouvel appareil, l'application demande un nouveau token pour chaque appareil auprès des serveurs FCM. Une fois que l'application a obtenu les tokens pour chaque appareil, elle les associe aux identifiants de compte de l'utilisateur sur les serveurs de l'application.

Lorsque l'utilisateur envoie un message à partir de l'une de ses applications sur l'un de ses appareils, le serveur de l'application envoie une notification push aux autres appareils de l'utilisateur. Lorsque l'application reçoit une notification push, elle peut afficher une alerte ou une notification à l'utilisateur pour lui indiquer qu'un nouveau message est arrivé.

En ce qui concerne la gestion des tokens sur plusieurs appareils, chaque token est spécifique à un appareil particulier, donc l'application doit enregistrer chaque token associé à chaque appareil pour chaque utilisateur. Lorsqu'un utilisateur utilise plusieurs appareils, l'application doit s'assurer que les messages sont envoyés à tous les appareils associés à l'utilisateur. Pour ce faire, l'application peut stocker les tokens de chaque appareil de l'utilisateur sur les serveurs de l'application, de sorte que les notifications push puissent être envoyées à tous les appareils.

## 1.5.Question théorique d’approfondissement
Pour améliorer l'api GraphQL, on pourrait compresser les données retournée et/ou utiliser protobuf.
Cela permtrait de rendre la taille des données envoyée encore plus petite. Cependant le principale problème
que l'on va rencontrer d'après nous, est la complexitée coté serveur. Il faudra donc avoir un schema protobuf
pour chaque possibilité ce qui est très peu pratique.