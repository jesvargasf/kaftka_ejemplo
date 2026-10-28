package cl.duoc.dsy1107.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import cl.duoc.dsy1107.demo.config.KafkaConfig;
import cl.duoc.dsy1107.demo.model.Pedido;

@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public PedidoService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public Pedido publicar(Pedido pedido) {
        String cliente = pedido.cliente() == null ? "" : pedido.cliente().trim();
        if (cliente.isBlank()) {
            throw new IllegalArgumentException("cliente es obligatorio");
        }
        Pedido publicado = new Pedido(cliente, pedido.monto());
        String texto = cliente + " compro " + publicado.monto();
        log.info("[PUBLICA] topic={} key={} valor={}", KafkaConfig.TOPIC, cliente, texto);
        kafkaTemplate.send(KafkaConfig.TOPIC, cliente, texto);
        return publicado;
    }
}
