import org.apache.zookeeper.*;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.Stat;

import static org.apache.zookeeper.Watcher.Event.EventType.*;

public class Chat {
    private static final String HOST = "localhost";
    private static final String PORTA = "2181";
    private static final int TIMEOUT = 5000;
    private ZooKeeper zooKeeper;

    //para abrigar todas as mensagens do chat
    private static final String ZNODE_CHAT = "/chat";
    //para abrigar todos os usuários atualmente logados no chat
    private static final String ZNODE_USUARIOS = "/usuarios";
    //para ler os dados que o usuário vai digitar
    private Scanner scanner;
    //menu de opções a ser exibido para o usuário, uma vez que ele tenha logado no chat
    private String instrucoes =
            String.format(
                    "%s\n%s\n%s\n",
                    "/list: exibe as mensagens",
                    "/send msg: envia msg para todos",
                    "/exit: encerra execução"
            );
    //para formatar a data de uma mensagem, quando ela tiver de ser exibida
    private static SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy kk:mm:ss");
    //nome escolhido pelo usuário para entrar no chat
    private String usuario;

    //construa o Scanner aqui
    private Chat (){
        scanner = new Scanner(System.in);
    }

    //Mostra uma mensagem para o usuário, dizendo que ele deve escolher seu nome
    //usa o scanner para capturar o nome
    //verifica se o usuário já existe, com auxilio do método usuarioJaExiste
    //também verifica se o nome escolhido tem o caractere /
    //quando um nome válido for digitado, ele é atribuído à variável de instância usuário
    //deve criar um ZNode efêmero para representar o usuário
    private void capturaUsuario () throws InterruptedException, KeeperException{
        //seu código aqui
        Pattern special = Pattern.compile("/");
        while(true) {
            System.out.println("Escolha um nome de usuário para você: \n");
            usuario= scanner.nextLine();
            Matcher hasSpecial = special.matcher(usuario);
            if (usuario.isEmpty() || hasSpecial.find()) {
                System.out.println("Nome não aceito. Por favor escreva novamente.");
            } else if (usuarioJaExiste(usuario)) {
                System.out.println("Nome já está sendo usado. Use outro.");
            }else{
                String prefixo = String.format("%s/%s", ZNODE_USUARIOS, usuario);
                String pathInteiro = zooKeeper.create(prefixo, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        CreateMode.EPHEMERAL);
                break;
            }
        }
        //no final, esse método pode mostrar essa mensagem
        System.out.printf ("Oi, %s. Você entrou. Veja o que já aconteceu até então.\n", usuario);
    }

    //verifica se o nome de usuário especificado possui um ZNode na árvore do ZKeeper
    //você pode usar o método getChildren para isso
    private boolean usuarioJaExiste (String usuario) throws InterruptedException, KeeperException{
        //seu código aqui
        List<String> listaUsuarios = zooKeeper.getChildren(ZNODE_USUARIOS,false);
        //ao final, devolva uma expressão booleana
        return listaUsuarios.contains(usuario);
    }

    //veja os comentários para implementar esse método
    private void exibirHistorico () throws InterruptedException, KeeperException{
        //obter a lista de mensagens (getChildren)
        List<String> datas = zooKeeper.getChildren(ZNODE_CHAT,false); //chame o getChildren aqui
        //se estiver vazia, mostra a mensagem especificada
        if (datas.isEmpty()) {
            System.out.println("Não há mensagens.");
            return;
        }
        //ordene pela data (você pode usar o sort de Collections que recebe a lista e um Comparator)
        //ordene aqui
        Collections.sort(datas);
        for (String data : datas){
            //obtém os dados do ZNode da vez (com getData)
            byte [] bytes = zooKeeper.getData(
                    String.format("%s/%s", ZNODE_CHAT, data),
                    false,
                    zooKeeper.exists(
                            String.format("%s/%s", ZNODE_CHAT, data),
                            false
                    )
            );
            //formatar e exibir no padrão data: Usuário diz Oi, Tudo bem?
            //seu código aqui
            System.out.println(data + " " + new String(bytes));
            System.out.println("************************");
        }
    }


    private void executar() throws InterruptedException, KeeperException{

        String opcao = "";
        //exibir instruções
        exibirInstrucoes();
        //capturar opcao do usuário
        opcao = scanner.nextLine();
        while (!opcao.equals("/exit")){
            //list: exibe o histórico de mensagens e as instruções
            if (opcao.startsWith("/list")){
                //seu código aqui
                exibirHistorico();
                exibirInstrucoes();
            }
            //send
            else if (opcao.startsWith("/send")){
                //extrai a data atual do sistema (new Date())
                Date date = new Date();
                // a representa como um número (new Date().getTime())
                String data_string = formatDate(date.getTime());
                //e cria um ZNode persistente
                String prefixo = String.format("%s/%s", ZNODE_CHAT,  data_string);
                //o nome do ZNode é o número que representa a data
                //seu conteúdo pode ser algo como usuario:mensagem
                String msg = opcao.substring(opcao.indexOf(" ") + 1);
                String conteudo = usuario + " diz " + msg;

                String pathInteiro = zooKeeper.create(prefixo, conteudo.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        CreateMode.PERSISTENT);
            }
            else{
                System.out.println("Opção inválida.");
            }
            opcao = scanner.nextLine();
        }
        System.out.println("Até mais.");
    }


    private void criarNosRaizes () throws InterruptedException, KeeperException{
        //criar os dois ZNodes (/chat e /usuarios) usando o método criarNoRaiz
        criarNoRaiz(ZNODE_CHAT);
        criarNoRaiz(ZNODE_USUARIOS);
    }

    private void registrarWatchers() throws InterruptedException, KeeperException{
        //registrar watcher persistente e recursivo no ZNode /usuarios
        //use o método addWatch
        String prefixo = ZNODE_USUARIOS+'/';
        zooKeeper.addWatch(ZNODE_USUARIOS, new Watcher() {
                    @Override
                    public void process(WatchedEvent event) {
                        String eventPath = event.getPath();
                        String nome = eventPath.substring(prefixo.length());
                        switch(event.getType()){
                            case NodeCreated:
                                System.out.println(nome + " entrou\n");
                                exibirInstrucoes();
                                break;
                            case NodeDeleted:
                                if(!usuario.equals(nome)) {
                                    System.out.println(nome + " saiu\n");
                                    exibirInstrucoes();
                                }
                                break;
                        }
                    }
                },
                AddWatchMode.PERSISTENT_RECURSIVE);

        //registrar um one-time trigger watch no ZNode /chat
        //use getChildren.
        //Use o watch historicoWatcher implementado logo a seguir
        zooKeeper.getChildren(ZNODE_CHAT, historicoWatcher);

    }
    private  final Watcher historicoWatcher = new Watcher() {
        @Override
        public void process(WatchedEvent event) {
            try {
                if (event.getState() == Event.KeeperState.SyncConnected){
                    //exibe histórico
                    //exibe instruções
                    //registra um novo watch one-time trigger no ZNode /chat
                    exibirHistorico();
                    exibirInstrucoes();
                    zooKeeper.getChildren(ZNODE_CHAT, historicoWatcher);
                }
            } catch (InterruptedException | KeeperException e) {
                e.printStackTrace();
            }
        }
    };

    private void exibirInstrucoes (){
        //um simples println para exibir as instruções
        System.out.println(instrucoes);
    }



    //formatando a data
    //já está pronto
    private static String formatDate (long date){
        return sdf.format(new Date(date));
    }

    //método main já está pronto
    //analise a ordem de execução dos métodos
    //para entender o funcionamento do sistema
    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
        Chat chat = new Chat();
        chat.conectar();
        chat.criarNosRaizes();
        chat.capturaUsuario();
        chat.exibirHistorico();
        chat.registrarWatchers();
        chat.executar();
        chat.fechar();
    }

    //cria um ZNode com o nome especificado
    //já está pronto
    public void criarNoRaiz(String nome) throws InterruptedException, KeeperException{
        Stat stat = zooKeeper.exists(nome, false);
        if (stat == null)
            zooKeeper.create(nome, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

    }

    //fecha conexão com o Zookeeper e o scanner.
    //já está pronto
    public void fechar () throws InterruptedException{
        zooKeeper.close();
        scanner.close();
    }

    //conexão com o Zookeeper
    //já está pronto
    public void conectar () throws IOException{
        zooKeeper = new ZooKeeper(
                String.format("%s:%s", HOST, PORTA),
                TIMEOUT,
                event -> {
                    if (event.getType() == None){
                        if (event.getState() == Watcher.Event.KeeperState.SyncConnected){
                            System.out.println ("Conectou!!");
                            System.out.printf("Estamos na thread: %s\n", Thread.currentThread().getName());
                        }
                        else if (event.getState() == Watcher.Event.KeeperState.Disconnected){
                            synchronized (zooKeeper){
                                System.out.println ("Desconectou...");
                                System.out.println ("Estamos na thread: " + Thread.currentThread().getName());
                            }
                        }
                    }
                }
        );
    }
}
