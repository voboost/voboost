//! Root-owned interoperability tests compile the actual production producer and consumer.
#[path = "../../../voboost-installer/src-tauri/src/log_stream.rs"]
#[allow(dead_code)]
mod consumer;
#[path = "../../../voboost-inject/daemon/src/log_export.rs"]
mod producer;

#[test]
fn daemon_stream_is_consumed_without_changing_records_or_device_context() {
    let records = b"{\"schemaVersion\":1,\"sequence\":1,\"timestamp\":\"2026-10-28T00:00:00.000Z\",\"source\":{\"kind\":\"daemon\"},\"level\":\"info\",\"component\":\"install\",\"message\":\"Ready\",\"fields\":{}}\n";
    let context = br#"{"bootId":"01234567-89ab-cdef-0123-456789abcdef","complete":true}"#;
    let frame = producer::encode(records, context).unwrap();
    assert_eq!(&frame[..11], b"VBOOSTLOG1\x01");
    assert_eq!(
        u64::from_be_bytes(frame[15..23].try_into().unwrap()) as usize,
        records.len() + context.len()
    );
    let decoded = consumer::parse_export(frame).unwrap();
    assert_eq!(decoded.records, records);
    assert_eq!(decoded.context, context);
    assert_eq!(decoded.header["schemaVersion"], 1);
    assert_eq!(decoded.header["recordsLength"], records.len());
    assert!(decoded.host_envelope.device_serial.is_none());
    assert_eq!(decoded.host_envelope.completeness, "unavailable");
}

#[test]
fn consumer_rejects_real_stream_corruption_truncation_and_trailing_bytes() {
    let frame = producer::encode(b"{\"message\":\"event\"}\n", b"{}").unwrap();
    for end in [0, 10, 54, frame.len() - 1] {
        assert!(consumer::parse_export(frame[..end].to_vec()).is_err());
    }
    let mut altered = frame.clone();
    *altered.last_mut().unwrap() ^= 1;
    assert!(consumer::parse_export(altered).is_err());
    let mut extra = frame.clone();
    extra.push(0);
    assert!(consumer::parse_export(extra).is_err());
    let mut oversize = frame;
    oversize[15..23].copy_from_slice(&u64::MAX.to_be_bytes());
    assert!(consumer::parse_export(oversize).is_err());
}

#[test]
fn malformed_record_and_context_are_rejected_even_with_a_valid_stream_digest() {
    for (records, context) in [
        (b"not-json\n".as_slice(), b"{}".as_slice()),
        (b"{}\n".as_slice(), b"not-json".as_slice()),
    ] {
        let frame = producer::encode(records, context).unwrap();
        assert!(consumer::parse_export(frame).is_err());
    }
}
