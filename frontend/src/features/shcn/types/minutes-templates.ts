export type TemplateField={maTruong:string;nhan:string;kieuDuLieu:string;batBuoc:boolean;nguonDien:string;maNguonHeThong:string|null};
export type TemplateSection={maMuc:string;tieuDe:string;thuTuHienThi:number;loaiMuc:string;batBuoc:boolean;cauHinhTruong:TemplateField[];laMucHeThong:boolean};
export type TemplateDictionary={kieuDuLieu:string[];nguonHeThong:{ma:string;nhan:string;kieuDuLieu:string}[];mucHeThong:TemplateSection[]};
export type TemplateDraft={tenMau:string;muc:TemplateSection[]};
export type MinutesTemplate={id:string|null;dotId:string;dotVersion:string;daCoMau:boolean;duocSua:boolean;tenMau:string|null;trangThai:string|null;muc:TemplateSection[];danhMuc:TemplateDictionary};
export type TemplateWrite={expectedVersion:string;tenMau:string;muc:Omit<TemplateSection,'laMucHeThong'>[]};
