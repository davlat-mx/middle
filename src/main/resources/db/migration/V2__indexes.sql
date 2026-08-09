create index idx_transfer_status on transfer (status);
create index idx_transfer_created_at on transfer (created_at);
create index idx_transfer_sender on transfer (sender_id);
create index idx_transfer_corridor on transfer (corridor_from, corridor_to);
create index idx_event_transfer on transfer_event (transfer_id);
